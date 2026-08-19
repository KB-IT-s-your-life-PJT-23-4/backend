package com.example.project.simulation.service;

import com.example.project.simulation.domain.BaseRateRecord;
import com.example.project.simulation.domain.DeductionRule;
import com.example.project.simulation.domain.FamilySnapshot;
import com.example.project.simulation.domain.GiftHistoryRecord;
import com.example.project.simulation.domain.PreferentialRateRecord;
import com.example.project.simulation.domain.ProductCandidate;
import com.example.project.simulation.domain.ProductDataVersionRecord;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.ProductVersionDetailRecord;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxBracket;
import com.example.project.simulation.domain.TaxPaymentMethod;
import com.example.project.simulation.dto.request.SimulationExecuteRequest;
import com.example.project.simulation.dto.request.SimulationSaveRequest;
import com.example.project.simulation.dto.response.SimulationResponse;
import com.example.project.simulation.dto.response.EtfVolatilityResponse;
import com.example.project.simulation.dto.response.SimulationSaveResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.mapper.SimulationMapper;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.crypto.UserPiiProtectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class SimulationService {

    public static final String FORMULA_VERSION = "INVESTMENT_V6";
    public static final String CALCULATION_VERSION = "GIFT_SIM_V7";

    private static final int DEDUCTION_WINDOW_YEARS = 10;
    private static final int ADULT_AGE = 19;
    private static final int MAX_PRODUCT_CANDIDATES = 3;
    private static final int MAX_INVESTMENT_MONTHS = 240;
    static final Period DRAFT_RETENTION = Period.ofMonths(1);
    private static final long CALCULATION_TOLERANCE_WON = 1L;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final SimulationMapper simulationMapper;
    private final UserMapper userMapper;
    private final SimulationCalculator calculator;
    private final SimulationIdempotencyStore idempotencyStore;
    private final EtfVolatilityCalculator volatilityCalculator;
    private final UserPiiProtectionService piiProtectionService;

    @Transactional
    public SimulationResponse execute(
            SimulationExecuteRequest request,
            Long userId,
            String idempotencyKey
    ) {
        try {
            validateUser(userId);
            validateExecuteRequest(request);
            LocalDate asOfDate = LocalDate.now();
            LocalDate giftDate = request.getGiftDate();
            String fingerprint = executeFingerprint(request, asOfDate);
            var cached = idempotencyStore.find(
                    userId,
                    "POST:/api/gs",
                    idempotencyKey,
                    fingerprint,
                    SimulationResponse.class
            );
            if (cached.isPresent()) {
                return cached.get();
            }

            FamilySnapshot family = requireFamily(request.getFamilyId(), userId);
            long deductionLimit = resolveDeductionLimit(family, giftDate);

            LocalDate lookbackStart = giftDate.minusYears(DEDUCTION_WINDOW_YEARS);
            List<GiftHistoryRecord> completedGifts = safeList(
                    simulationMapper.selectCompletedGifts(
                            family.getFamilyId(),
                            lookbackStart,
                            giftDate
                    )
            );
            long previousGiftAmount = completedGifts.stream()
                    .mapToLong(gift -> value(gift.getAmount()))
                    .sum();
            long usedDeduction = Math.min(previousGiftAmount, deductionLimit);
            long remainingDeduction = Math.max(0, deductionLimit - usedDeduction);

            List<TaxBracket> taxBrackets = safeList(simulationMapper.selectTaxBrackets(asOfDate));
            if (taxBrackets.isEmpty()) {
                throw new SimulationException(SimulationError.TAX_BRACKET_NOT_FOUND);
            }

            ProductDataVersionRecord productDataVersion =
                    simulationMapper.selectLatestCompletedProductDataVersion();
            if (productDataVersion == null) {
                throw new SimulationException(SimulationError.PRODUCT_DATA_NOT_READY);
            }

            Map<ProductType, List<ProductCandidate>> safeCandidates =
                    loadSafeAssetCandidates(
                            productDataVersion.getProductDataVersionId(),
                            request.getInvestmentPeriodMonths()
                    );
            Map<RiskProfile, List<ProductCandidate>> etfCandidates =
                    loadEtfCandidates(productDataVersion.getProductDataVersionId());

            LocalDate investmentEndDate =
                    giftDate.plusMonths(request.getInvestmentPeriodMonths());
            ScenarioAggregate immediate = immediateScenario(
                    request,
                    previousGiftAmount,
                    deductionLimit,
                    taxBrackets,
                    giftDate
            );
            ScenarioAggregate optimized = optimizedScenario(
                    request,
                    remainingDeduction,
                    taxBrackets,
                    giftDate,
                    completedGifts,
                    investmentEndDate,
                    family.getBirthDate().plusYears(ADULT_AGE),
                    date -> resolveDeductionLimit(family, date)
            );
            LocalDate evaluationDate = resolveEvaluationDate(
                    investmentEndDate,
                    request.getInvestmentPeriodMonths(),
                    optimized.tranches().size() > 1
            );
            LocalDate renewalDate = resolveDeductionRenewalDate(
                    completedGifts,
                    optimized.tranches(),
                    giftDate
            );

            LocalDateTime now = LocalDateTime.now();
            SimulationRecord simulation = new SimulationRecord();
            simulation.setProductDataVersionId(productDataVersion.getProductDataVersionId());
            simulation.setFamilyId(family.getFamilyId());
            simulation.setRequestedAmount(request.getRequestedAmount());
            simulation.setStatus(SimulationStatus.DRAFT);
            simulation.setTaxPaymentMethod(request.getTaxPaymentMethod());
            simulation.setInvestmentPeriodMonths(request.getInvestmentPeriodMonths());
            simulation.setAsOfDate(asOfDate);
            simulation.setGiftDate(giftDate);
            simulation.setInvestmentEndDate(investmentEndDate);
            simulation.setCalculationVersion(CALCULATION_VERSION);
            simulation.setFormulaVersion(FORMULA_VERSION);
            simulation.setVersion(1L);
            simulation.setPreviousGiftAmount(previousGiftAmount);
            simulation.setDeductionLimit(deductionLimit);
            simulation.setDeductionRenewalDate(renewalDate);
            simulation.setCreatedAt(now);
            simulation.setUpdatedAt(now);
            simulation.setExpiredAt(now.plus(DRAFT_RETENTION));
            simulationMapper.insertSimulation(simulation);

            PersistedScenario immediatePersisted = persistScenario(
                    simulation.getSimulationId(),
                    immediate,
                    safeCandidates,
                    etfCandidates,
                    request.getInvestmentPeriodMonths(),
                    evaluationDate,
                    now
            );
            PersistedScenario optimizedPersisted = persistScenario(
                    simulation.getSimulationId(),
                    optimized,
                    safeCandidates,
                    etfCandidates,
                    request.getInvestmentPeriodMonths(),
                    evaluationDate,
                    now
            );
            markRecommendations(immediatePersisted, optimizedPersisted);

            SimulationResponse response;
            try {
                response = get(simulation.getSimulationId(), userId);
            } catch (SimulationException exception) {
                if (exception.getError() == SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE
                        || exception.getError()
                        == SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE) {
                    throw new SimulationException(
                            SimulationError.SIMULATION_RESULT_INCOMPLETE,
                            exception
                    );
                }
                throw exception;
            }
            idempotencyStore.remember(
                    userId,
                    "POST:/api/gs",
                    idempotencyKey,
                    fingerprint,
                    response
            );
            return response;
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Simulation execution failed", exception);
            throw new SimulationException(SimulationError.SIMULATION_EXECUTION_FAILED, exception);
        }
    }

    @Transactional(readOnly = true)
    public SimulationResponse get(Long simulationId, Long userId) {
        try {
            validateUser(userId);
            validateSimulationId(simulationId);
            SimulationRecord simulation = requireSimulation(simulationId, userId);
            return buildResponse(simulation);
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Simulation read failed. simulationId={}", simulationId, exception);
            throw new SimulationException(SimulationError.SIMULATION_READ_FAILED, exception);
        }
    }

    @Transactional
    public SimulationSaveResponse save(
            Long simulationId,
            SimulationSaveRequest request,
            Long userId,
            String idempotencyKey
    ) {
        try {
            validateUser(userId);
            validateSaveRequest(simulationId, request);
            String fingerprint = saveFingerprint(simulationId, request);
            String operation = "PATCH:/api/gs/" + simulationId + "/save";
            var cached = idempotencyStore.find(
                    userId,
                    operation,
                    idempotencyKey,
                    fingerprint,
                    SimulationSaveResponse.class
            );
            if (cached.isPresent()) {
                return cached.get();
            }

            SimulationRecord target = requireSimulation(simulationId, userId);
            FamilySnapshot lockedFamily = simulationMapper.lockFamily(target.getFamilyId());
            if (lockedFamily == null || !Objects.equals(lockedFamily.getUserId(), userId)) {
                throw new SimulationException(SimulationError.SIMULATION_ACCESS_DENIED);
            }
            cached = idempotencyStore.find(
                    userId,
                    operation,
                    idempotencyKey,
                    fingerprint,
                    SimulationSaveResponse.class
            );
            if (cached.isPresent()) {
                return cached.get();
            }
            target = requireSimulation(simulationId, userId);
            if (!Objects.equals(target.getVersion(), request.getVersion())) {
                throw new SimulationException(
                        SimulationError.SIMULATION_VERSION_CONFLICT,
                        versionConflictData(request.getVersion(), target.getVersion())
                );
            }
            SimulationRecord activeSaved =
                    simulationMapper.selectSavedSimulationByFamily(target.getFamilyId());
            boolean replacingAnother = activeSaved != null
                    && !Objects.equals(activeSaved.getSimulationId(), simulationId);
            validateReplacementIntent(request, activeSaved, replacingAnother);

            SimulationPortfolioRecord selectedPortfolio =
                    simulationMapper.selectPortfolio(request.getSelectedPortfolioId());
            if (selectedPortfolio == null) {
                throw new SimulationException(SimulationError.PORTFOLIO_NOT_FOUND);
            }
            if (!Objects.equals(selectedPortfolio.getSimulationId(), simulationId)) {
                throw new SimulationException(SimulationError.PORTFOLIO_NOT_IN_SIMULATION);
            }
            if (!selectedPortfolio.isRecommended()) {
                throw new SimulationException(SimulationError.PORTFOLIO_NOT_RECOMMENDED);
            }

            List<SimulationProductRecord> portfolioProducts =
                    safeList(simulationMapper.selectPortfolioProducts(selectedPortfolio.getPortfolioId()));

            if (request.getClientCalculation() != null
                    && !FORMULA_VERSION.equals(
                    request.getClientCalculation().getFormulaVersion())) {
                throw new SimulationException(SimulationError.CALCULATION_VERSION_CONFLICT);
            }

            List<SimulationResultRecord> allResults =
                    safeList(simulationMapper.selectResults(simulationId));
            List<SimulationTrancheRecord> allTranches =
                    safeList(simulationMapper.selectTranches(simulationId));
            List<SimulationTrancheRecord> selectedTranches = allTranches.stream()
                            .filter(item -> Objects.equals(
                                    item.getResultId(),
                                    selectedPortfolio.getResultId()
                            ))
                            .toList();
            SimulationResultRecord selectedResult = allResults.stream()
                            .filter(item -> Objects.equals(
                                    item.getResultId(),
                                    selectedPortfolio.getResultId()
                            ))
                            .findFirst()
                            .orElseThrow(() -> new SimulationException(
                                    SimulationError.SIMULATION_RESULT_INCOMPLETE));
            LocalDate evaluationDate = resolveEvaluationDate(
                    target,
                    allResults,
                    allTranches
            );
            long allocatedAmount = value(selectedPortfolio.getDepositAmount())
                    + value(selectedPortfolio.getSavingsAmount())
                    + value(selectedPortfolio.getEtfAmount());
            if (allocatedAmount != value(selectedResult.getInvestmentPrincipal())) {
                throw new SimulationException(
                        SimulationError.PORTFOLIO_ALLOCATION_MISMATCH);
            }
            ProductSelectionPlan selectionPlan = validateProductSelections(
                    request,
                    target,
                    selectedPortfolio,
                    portfolioProducts,
                    selectedTranches,
                    evaluationDate
            );

            SimulationSaveResponse.PreviousSimulation previousSimulation = null;
            LocalDateTime now = LocalDateTime.now();
            if (replacingAnother) {
                LocalDateTime previousExpiry = now.plus(DRAFT_RETENTION);
                int reset = simulationMapper.resetSavedSimulation(
                        activeSaved.getSimulationId(),
                        previousExpiry,
                        now
                );
                if (reset != 1) {
                    throw new SimulationException(
                            SimulationError.PREVIOUS_SIMULATION_RESET_FAILED);
                }
                restoreSimulationProducts(activeSaved);
                simulationMapper.deleteSimulationPreferentialConditions(
                        activeSaved.getSimulationId());
                simulationMapper.clearSimulationSelections(
                        activeSaved.getSimulationId());
                previousSimulation = new SimulationSaveResponse.PreviousSimulation(
                        activeSaved.getSimulationId(),
                        SimulationStatus.SAVED,
                        SimulationStatus.DRAFT,
                        activeSaved.getVersion() + 1,
                        previousExpiry
                );
            }

            restoreSimulationProducts(target);
            simulationMapper.deleteSimulationPreferentialConditions(simulationId);
            simulationMapper.clearSimulationSelections(simulationId);

            long serverFutureValue = 0;
            List<SimulationProductRecord> selectedProducts = new ArrayList<>();
            for (SelectedProductPlan productPlan : selectionPlan.products()) {
                SimulationProductRecord product = productPlan.product();
                hydrateBaseRateTiers(product);
                BigDecimal appliedRate = representativeAppliedRate(
                        product,
                        productPlan.preferentialRates(),
                        selectedTranches,
                        evaluationDate
                );
                product.setAppliedAnnualRatePercent(appliedRate);
                long futureValue = calculateSelectedProductValue(
                        product,
                        product.getAllocatedAmount(),
                        selectedTranches,
                        selectedResult.getInvestmentPrincipal(),
                        evaluationDate,
                        productPlan.preferentialRates()
                );
                product.setSelected(true);
                product.setExpectedFutureValue(futureValue);
                product.setSelectedPreferentialConditions(productPlan.preferentialRates());
                int selected = simulationMapper.markSimulationProductSelected(
                        product.getSimulationProductId(),
                        appliedRate,
                        futureValue
                );
                if (selected != 1) {
                    throw new SimulationException(
                            SimulationError.SIMULATION_SAVE_FAILED);
                }
                for (PreferentialRateRecord rate : productPlan.preferentialRates()) {
                    int inserted = simulationMapper.insertSelectedPreferentialCondition(
                            product.getSimulationProductId(),
                            rate.getPreferentialInterestRateId()
                    );
                    if (inserted != 1) {
                        throw new SimulationException(
                                SimulationError.SIMULATION_SAVE_FAILED);
                    }
                }
                selectedProducts.add(product);
                serverFutureValue += futureValue;
            }

            int updated = simulationMapper.saveSimulation(
                    simulationId,
                    request.getVersion(),
                    selectedPortfolio.getPortfolioId(),
                    now
            );
            if (updated != 1) {
                SimulationRecord current = simulationMapper.selectSimulation(simulationId);
                throw new SimulationException(
                        SimulationError.SIMULATION_VERSION_CONFLICT,
                        versionConflictData(
                                request.getVersion(),
                                current == null ? null : current.getVersion()
                        )
                );
            }

            long serverProfit = serverFutureValue - selectedResult.getInvestmentPrincipal();
            SimulationSaveResponse.ClientServerDifference difference =
                    clientServerDifference(request, serverFutureValue, serverProfit);
            boolean adjusted = difference != null && (
                    Math.abs(difference.futureValueDifference()) > CALCULATION_TOLERANCE_WON
                            || Math.abs(difference.profitDifference())
                            > CALCULATION_TOLERANCE_WON
            );
            SimulationResponse.Selection selection = toSelection(
                    selectedPortfolio,
                    selectedResult,
                    selectedProducts,
                    selectedTranches,
                    target,
                    evaluationDate,
                    volatilityByProductId(
                            selectedProducts,
                            target.getAsOfDate()
                    )
            );
            SimulationSaveResponse response = new SimulationSaveResponse(
                    simulationId,
                    SimulationStatus.SAVED,
                    request.getVersion() + 1,
                    new SimulationSaveResponse.Replacement(
                            replacingAnother,
                            previousSimulation
                    ),
                    selection,
                    new SimulationSaveResponse.ServerCalculation(
                            FORMULA_VERSION,
                            serverFutureValue,
                            serverProfit
                    ),
                    adjusted,
                    difference,
                    now,
                    now,
                    null
            );
            rememberSaveAfterCommit(
                    userId, operation, idempotencyKey, fingerprint, response);
            return response;
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Simulation save failed. simulationId={}", simulationId, exception);
            throw new SimulationException(SimulationError.SIMULATION_SAVE_FAILED, exception);
        }
    }

    SimulationResponse buildResponse(SimulationRecord simulation) {
        Long simulationId = simulation.getSimulationId();
        SnapshotDerivedValues derived = deriveSnapshotValues(simulation);
        List<SimulationResultRecord> results =
                safeList(simulationMapper.selectResults(simulationId));
        List<SimulationTrancheRecord> tranches =
                safeList(simulationMapper.selectTranches(simulationId));
        List<SimulationPortfolioRecord> portfolios =
                safeList(simulationMapper.selectPortfolios(simulationId));
        List<SimulationProductRecord> products =
                safeList(simulationMapper.selectProductSnapshots(simulationId));
        ProductDataVersionRecord productDataVersion =
                simulationMapper.selectProductDataVersion(
                        simulation.getProductDataVersionId()
                );
        validateSnapshotCompleteness(
                simulation,
                results,
                tranches,
                portfolios,
                products,
                productDataVersion
        );
        if (simulation.getStatus() == SimulationStatus.DRAFT
                && simulation.getSelectedPortfolioId() == null
                && products.stream().anyMatch(SimulationProductRecord::isSelected)) {
            throw incompleteSnapshot();
        }

        Map<Long, List<SimulationTrancheRecord>> tranchesByResult = tranches.stream()
                .collect(Collectors.groupingBy(SimulationTrancheRecord::getResultId));
        Map<Long, List<SimulationPortfolioRecord>> portfoliosByResult = portfolios.stream()
                .collect(Collectors.groupingBy(SimulationPortfolioRecord::getResultId));
        Map<Long, List<SimulationProductRecord>> productsByPortfolio = products.stream()
                .collect(Collectors.groupingBy(SimulationProductRecord::getPortfolioId));
        Map<Long, SimulationResultRecord> resultById = results.stream()
                .collect(Collectors.toMap(
                        SimulationResultRecord::getResultId,
                        Function.identity()
                ));
        Map<Long, EtfVolatilityResponse> volatilityByProductId =
                volatilityByProductId(products, simulation.getAsOfDate());
        LocalDate evaluationDate = resolveEvaluationDate(
                simulation,
                results,
                tranches
        );

        List<SimulationResponse.Recommendation> recommendations = portfolios.stream()
                .filter(SimulationPortfolioRecord::isRecommended)
                .sorted(Comparator.comparing(item -> item.getPortfolioType().ordinal()))
                .map(item -> new SimulationResponse.Recommendation(
                        item.getPortfolioType(),
                        item.getScenarioType(),
                        item.getResultId(),
                        item.getPortfolioId()
                ))
                .toList();
        if (recommendations.size() != RiskProfile.values().length
                || recommendations.stream()
                .map(SimulationResponse.Recommendation::portfolioType)
                .distinct().count() != RiskProfile.values().length) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE);
        }

        SimulationResponse.Selection selection = null;
        if (simulation.getSelectedPortfolioId() != null) {
            SimulationPortfolioRecord selectedPortfolio = portfolios.stream()
                    .filter(item -> Objects.equals(
                            item.getPortfolioId(),
                            simulation.getSelectedPortfolioId()
                    ))
                    .findFirst()
                    .orElseThrow(this::incompleteSavedSelection);
            SimulationResultRecord selectedResult = resultById.get(
                    selectedPortfolio.getResultId()
            );
            List<SimulationProductRecord> selectedProducts =
                    productsByPortfolio.getOrDefault(
                                    selectedPortfolio.getPortfolioId(),
                                    List.of()
                            ).stream()
                            .filter(SimulationProductRecord::isSelected)
                            .peek(product -> product.setSelectedPreferentialConditions(
                                    safeList(simulationMapper.selectSelectedPreferentialRates(
                                            product.getSimulationProductId()
                                    ))
                            ))
                            .toList();
            validateSavedSelection(
                    selectedPortfolio,
                    selectedResult,
                    selectedProducts,
                    products
            );
            selection = toSelection(
                    selectedPortfolio,
                    selectedResult,
                    selectedProducts,
                    tranchesByResult.getOrDefault(
                            selectedResult.getResultId(),
                            List.of()
                    ),
                    simulation,
                    evaluationDate,
                    volatilityByProductId
            );
        }

        // 저장된 우대조건을 선택 상품에 먼저 복원한 뒤 결과 상품 후보 응답을 만든다.
        // 응답을 먼저 만들면 selection에는 조건이 있어도 productCandidates에는 빠져
        // 이력의 "결과 다시 보기" 화면에서 체크가 해제되어 보인다.
        List<SimulationResponse.Result> responseResults = results.stream()
                .map(result -> new SimulationResponse.Result(
                        result.getResultId(),
                        result.getScenarioType(),
                        result.getDeductionAmount(),
                        result.getTaxableAmount(),
                        result.getGiftTax(),
                        result.getDonorRequiredAmount(),
                        result.getPostTaxAmount(),
                        result.getInvestmentPrincipal(),
                        tranchesByResult.getOrDefault(result.getResultId(), List.of())
                                .stream()
                                .map(this::toTrancheResponse)
                                .toList(),
                        portfoliosByResult.getOrDefault(result.getResultId(), List.of())
                                .stream()
                                .map(portfolio -> toPortfolioResponse(
                                        portfolio,
                                        result,
                                        productsByPortfolio.getOrDefault(
                                                portfolio.getPortfolioId(),
                                                List.of()
                                        ),
                                        tranchesByResult.getOrDefault(
                                                result.getResultId(),
                                                List.of()
                                        ),
                                        simulation,
                                        evaluationDate,
                                        volatilityByProductId
                                ))
                                .toList()
                ))
                .toList();

        return new SimulationResponse(
                simulationId,
                simulation.getStatus(),
                simulation.getVersion(),
                new SimulationResponse.Family(
                        simulation.getFamilyId(),
                        simulation.getFamilyName(),
                        simulation.getRelation(),
                        simulation.getBirthDate(),
                        derived.ageAtSimulation(),
                        derived.minorAtSimulation()
                ),
                new SimulationResponse.Input(
                        simulation.getRequestedAmount(),
                        simulation.getTaxPaymentMethod(),
                        simulation.getInvestmentPeriodMonths(),
                        simulation.getAsOfDate(),
                        simulation.getGiftDate(),
                        simulation.getInvestmentEndDate(),
                        evaluationDate
                ),
                new SimulationResponse.GiftHistorySummary(
                        derived.lookbackStartDate(),
                        derived.previousGiftAmount(),
                        derived.deductionLimit(),
                        derived.usedDeductionAmount(),
                        derived.remainingDeductionAmount(),
                        simulation.getDeductionRenewalDate()
                ),
                new SimulationResponse.ProductDataVersion(
                        productDataVersion.getProductDataVersionId(),
                        productDataVersion.getVersionCode(),
                        productDataVersion.getDataDate()
                ),
                recommendations,
                selection,
                responseResults,
                frontendPolicy(simulation.getInvestmentPeriodMonths()),
                simulation.getCalculationVersion(),
                simulation.getFormulaVersion(),
                simulation.getCreatedAt(),
                simulation.getUpdatedAt(),
                simulation.getSavedAt(),
                simulation.getExpiredAt()
        );
    }

    private PersistedScenario persistScenario(
            Long simulationId,
            ScenarioAggregate aggregate,
            Map<ProductType, List<ProductCandidate>> safeCandidates,
            Map<RiskProfile, List<ProductCandidate>> etfCandidates,
            int investmentPeriodMonths,
            LocalDate investmentEndDate,
            LocalDateTime now
    ) {
        SimulationResultRecord result = aggregate.result();
        result.setSimulationId(simulationId);
        result.setCreatedAt(now);
        simulationMapper.insertResult(result);
        for (SimulationTrancheRecord tranche : aggregate.tranches()) {
            tranche.setResultId(result.getResultId());
            simulationMapper.insertTranche(tranche);
        }

        Map<ProductType, List<ProductCandidate>> scenarioSafeCandidates =
                new EnumMap<>(ProductType.class);
        scenarioSafeCandidates.put(
                ProductType.DEPOSIT,
                candidatesForScenario(
                        safeCandidates.get(ProductType.DEPOSIT),
                        aggregate.tranches(),
                        investmentEndDate
                )
        );
        scenarioSafeCandidates.put(
                ProductType.SAVINGS,
                candidatesForScenario(
                        safeCandidates.get(ProductType.SAVINGS),
                        aggregate.tranches(),
                        investmentEndDate
                )
        );

        Map<RiskProfile, SimulationPortfolioRecord> persisted =
                new EnumMap<>(RiskProfile.class);
        for (RiskProfile profile : RiskProfile.values()) {
            Map<ProductType, BigDecimal> ratios =
                    PortfolioPolicy.allocations(investmentPeriodMonths).get(profile);
            Map<ProductType, Long> amounts = allocationAmounts(
                    result.getInvestmentPrincipal(),
                    ratios,
                    aggregate.tranches(),
                    scenarioSafeCandidates.get(ProductType.DEPOSIT).get(0),
                    scenarioSafeCandidates.get(ProductType.SAVINGS).get(0),
                    investmentEndDate
            );
            List<ProductCandidate> savingsCandidatesForAllocation =
                    scenarioSafeCandidates.get(ProductType.SAVINGS).stream()
                            .filter(candidate -> supportsSavingsAllocation(
                                    candidate,
                                    amounts.getOrDefault(ProductType.SAVINGS, 0L),
                                    aggregate.tranches(),
                                    investmentEndDate
                            ))
                            .toList();
            if (amounts.getOrDefault(ProductType.SAVINGS, 0L) > 0
                    && savingsCandidatesForAllocation.isEmpty()) {
                long savingsAmount = amounts.get(ProductType.SAVINGS);
                amounts.put(
                        ProductType.DEPOSIT,
                        amounts.getOrDefault(ProductType.DEPOSIT, 0L) + savingsAmount
                );
                amounts.put(ProductType.SAVINGS, 0L);
            }
            Map<ProductType, List<ProductCandidate>> candidates =
                    new EnumMap<>(ProductType.class);
            candidates.put(
                    ProductType.DEPOSIT,
                    scenarioSafeCandidates.get(ProductType.DEPOSIT)
            );
            candidates.put(
                    ProductType.SAVINGS,
                    savingsCandidatesForAllocation
            );
            candidates.put(ProductType.ETF, etfCandidates.get(profile));

            SimulationPortfolioRecord portfolio = new SimulationPortfolioRecord();
            portfolio.setResultId(result.getResultId());
            portfolio.setPortfolioType(profile);
            portfolio.setDepositAmount(amounts.get(ProductType.DEPOSIT));
            portfolio.setSavingsAmount(amounts.get(ProductType.SAVINGS));
            portfolio.setEtfAmount(amounts.get(ProductType.ETF));
            portfolio.setExpectedFutureValue(0L);
            portfolio.setRecommended(false);
            portfolio.setCreatedAt(now);
            portfolio.setUpdatedAt(now);
            simulationMapper.insertPortfolio(portfolio);

            long portfolioFutureValue = 0;
            for (ProductType type : ProductType.values()) {
                long allocatedAmount = amounts.getOrDefault(type, 0L);
                if (allocatedAmount <= 0) {
                    continue;
                }
                List<ProductCandidate> typeCandidates = candidates.get(type);
                if (typeCandidates == null || typeCandidates.isEmpty()) {
                    throw new SimulationException(
                            SimulationError.PRODUCT_CANDIDATE_NOT_FOUND);
                }
                long bestFutureValue = 0;
                for (ProductCandidate candidate : safeList(typeCandidates)) {
                    SimulationProductRecord product = toSnapshot(
                            portfolio.getPortfolioId(),
                            candidate,
                            allocatedAmount,
                            aggregate.tranches(),
                            result.getInvestmentPrincipal(),
                            investmentEndDate
                    );
                    simulationMapper.insertProductSnapshot(product);
                    bestFutureValue = Math.max(
                            bestFutureValue,
                            product.getExpectedFutureValue()
                    );
                }
                portfolioFutureValue += bestFutureValue;
            }
            portfolio.setExpectedFutureValue(portfolioFutureValue);
            updatePortfolioValue(portfolio);
            persisted.put(profile, portfolio);
        }
        return new PersistedScenario(result, persisted);
    }

    private List<ProductCandidate> candidatesForScenario(
            List<ProductCandidate> candidates,
            List<SimulationTrancheRecord> tranches,
            LocalDate evaluationDate
    ) {
        List<Integer> investmentPeriods = safeList(tranches).stream()
                .filter(tranche -> tranche.getGiftDate() != null
                        && !tranche.getGiftDate().isAfter(evaluationDate)
                        && value(tranche.getInvestmentAmount()) > 0)
                .map(tranche -> calculator.remainingMonths(
                        tranche.getGiftDate(),
                        evaluationDate
                ))
                .filter(months -> months > 0)
                .distinct()
                .toList();
        List<ProductCandidate> eligible = safeList(candidates).stream()
                .filter(candidate -> candidateSupportsAnyTranche(
                        candidate,
                        investmentPeriods
                ))
                .limit(MAX_PRODUCT_CANDIDATES)
                .toList();
        if (eligible.isEmpty()) {
            throw new SimulationException(
                    SimulationError.PRODUCT_CANDIDATE_NOT_FOUND);
        }
        return eligible;
    }

    private boolean candidateSupportsAnyTranche(
            ProductCandidate candidate,
            List<Integer> investmentPeriods
    ) {
        boolean hasInvestmentContract = false;
        for (Integer months : investmentPeriods) {
            SimulationCalculator.ReinvestmentPlan plan = calculator.reinvestmentPlan(
                    months,
                    candidate.getMinMonth(),
                    candidate.getMaxMonth()
            );
            if (!plan.hasInvestmentContract()) {
                continue;
            }
            try {
                plan.contractPeriods().forEach(period -> requiredRateTier(
                        candidate.getBaseRateTiers(),
                        period
                ));
                hasInvestmentContract = true;
            } catch (SimulationException exception) {
                if (exception.getError() == SimulationError.PRODUCT_DATA_NOT_READY) {
                    return false;
                }
                throw exception;
            }
        }
        return hasInvestmentContract;
    }

    private void updatePortfolioValue(SimulationPortfolioRecord portfolio) {
        // MyBatis keeps schema mutations explicit. This helper uses a mapper method
        // represented by re-inserting only in memory when running mapper fakes.
        // The real XML update is named updatePortfolioExpectedFutureValue.
        simulationMapper.updatePortfolioExpectedFutureValue(
                portfolio.getPortfolioId(),
                portfolio.getExpectedFutureValue()
        );
    }

    private void markRecommendations(
            PersistedScenario immediate,
            PersistedScenario optimized
    ) {
        for (RiskProfile profile : RiskProfile.values()) {
            SimulationPortfolioRecord first = immediate.portfolios().get(profile);
            SimulationPortfolioRecord second = optimized.portfolios().get(profile);
            SimulationPortfolioRecord selected;
            int valueCompare = Long.compare(
                    scenarioEndTotalValue(immediate, first),
                    scenarioEndTotalValue(optimized, second)
            );
            if (valueCompare > 0) {
                selected = first;
            } else if (valueCompare < 0) {
                selected = second;
            } else {
                selected = immediate.result().getGiftTax()
                        <= optimized.result().getGiftTax() ? first : second;
            }
            selected.setRecommended(true);
            simulationMapper.markPortfolioRecommended(selected.getPortfolioId());
        }
    }

    private long scenarioEndTotalValue(
            PersistedScenario scenario,
            SimulationPortfolioRecord portfolio
    ) {
        long remainingUninvestedPrincipal = Math.max(
                0,
                value(scenario.result().getPostTaxAmount())
                        - value(scenario.result().getInvestmentPrincipal())
        );
        try {
            return Math.addExact(
                    value(portfolio.getExpectedFutureValue()),
                    remainingUninvestedPrincipal
            );
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private ScenarioAggregate immediateScenario(
            SimulationExecuteRequest request,
            long previousGiftAmount,
            long deductionLimit,
            List<TaxBracket> brackets,
            LocalDate asOfDate
    ) {
        SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                request.getRequestedAmount(),
                previousGiftAmount,
                deductionLimit,
                request.getTaxPaymentMethod(),
                brackets
        );
        SimulationResultRecord result = result(
                ScenarioType.IMMEDIATE,
                tax.deductionAmount(),
                tax.taxableAmount(),
                tax.giftTax(),
                tax.donorRequiredAmount(),
                tax.investmentAmount(),
                tax.investmentAmount()
        );
        return new ScenarioAggregate(
                result,
                new ArrayList<>(List.of(tranche(
                        1,
                        asOfDate,
                        request.getRequestedAmount(),
                        tax,
                        true
                )))
        );
    }

    private ScenarioAggregate optimizedScenario(
            SimulationExecuteRequest request,
            long remainingDeduction,
            List<TaxBracket> brackets,
            LocalDate giftDate,
            List<GiftHistoryRecord> completedGifts,
            LocalDate investmentEndDate,
            LocalDate adulthoodDate,
            Function<LocalDate, Long> deductionLimitResolver
    ) {
        long amountLeft = request.getRequestedAmount();
        List<SimulationTrancheRecord> tranches = new ArrayList<>();
        List<GiftPoint> history = completedGifts.stream()
                .map(gift -> new GiftPoint(gift.getGiftDate(), value(gift.getAmount())))
                .collect(Collectors.toCollection(ArrayList::new));
        long totalDeduction = 0;
        long totalTaxable = 0;
        long totalTax = 0;
        long totalDonorRequired = 0;
        long totalPostTaxAmount = 0;
        long totalInvestment = 0;
        int sequence = 1;

        LocalDate trancheDate = giftDate;
        int guard = 0;
        while (amountLeft > 0 && guard++ < 100) {
            LocalDate calculationDate = trancheDate;
            long used = history.stream()
                    .filter(point -> isWithinDeductionWindow(
                            point.date(), calculationDate))
                    .mapToLong(GiftPoint::amount)
                    .sum();
            long trancheDeductionLimit = deductionLimitResolver.apply(trancheDate);
            long recalculatedAvailable = Math.max(0, trancheDeductionLimit - used);
            long available = trancheDate.equals(giftDate)
                    ? Math.min(Math.max(0, remainingDeduction), recalculatedAvailable)
                    : recalculatedAvailable;

            List<GiftPoint> nextDateBasis = new ArrayList<>(history);
            if (available > 0) {
                nextDateBasis.add(new GiftPoint(trancheDate, available));
            }
            LocalDate nextDate = nextDateBasis.isEmpty()
                    ? null
                    : nextPlanningDate(nextDateBasis, trancheDate, adulthoodDate);
            boolean hasNextDeductionDate = nextDate != null
                    && !nextDate.isAfter(investmentEndDate);
            long giftAmount = hasNextDeductionDate
                    ? Math.min(amountLeft, available)
                    : amountLeft;

            if (giftAmount == 0) {
                trancheDate = nextDate;
                continue;
            }
            SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                    giftAmount,
                    used,
                    trancheDeductionLimit,
                    request.getTaxPaymentMethod(),
                    brackets
            );
            tranches.add(tranche(sequence++, trancheDate, giftAmount, tax, true));
            history.add(new GiftPoint(trancheDate, giftAmount));
            amountLeft -= giftAmount;
            totalDeduction += tax.deductionAmount();
            totalTaxable += tax.taxableAmount();
            totalTax += tax.giftTax();
            totalDonorRequired += tax.donorRequiredAmount();
            totalPostTaxAmount += tax.investmentAmount();
            totalInvestment += tax.investmentAmount();

            if (amountLeft > 0) {
                trancheDate = nextPlanningDate(history, trancheDate, adulthoodDate);
            }
        }
        if (amountLeft > 0) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RESULT_INCOMPLETE);
        }

        SimulationResultRecord result = result(
                ScenarioType.TAX_OPTIMIZED,
                totalDeduction,
                totalTaxable,
                totalTax,
                totalDonorRequired,
                totalPostTaxAmount,
                totalInvestment
        );
        return new ScenarioAggregate(result, tranches);
    }

    private SimulationResultRecord result(
            ScenarioType type,
            long deduction,
            long taxable,
            long tax,
            long donorRequired,
            long postTax,
            long investmentPrincipal
    ) {
        SimulationResultRecord result = new SimulationResultRecord();
        result.setScenarioType(type);
        result.setDeductionAmount(deduction);
        result.setTaxableAmount(taxable);
        result.setGiftTax(tax);
        result.setDonorRequiredAmount(donorRequired);
        result.setPostTaxAmount(postTax);
        result.setInvestmentPrincipal(investmentPrincipal);
        return result;
    }

    private SimulationTrancheRecord tranche(
            int sequence,
            LocalDate giftDate,
            long giftAmount,
            SimulationCalculator.TaxOutcome tax,
            boolean included
    ) {
        SimulationTrancheRecord tranche = new SimulationTrancheRecord();
        tranche.setSequenceNo(sequence);
        tranche.setGiftDate(giftDate);
        tranche.setGiftAmount(giftAmount);
        tranche.setEstimatedGiftTax(tax.giftTax());
        tranche.setDonorRequiredAmount(tax.donorRequiredAmount());
        tranche.setInvestmentAmount(included ? tax.investmentAmount() : 0L);
        return tranche;
    }

    private Map<ProductType, Long> allocationAmounts(
            long principal,
            Map<ProductType, BigDecimal> ratios,
            List<SimulationTrancheRecord> tranches,
            ProductCandidate depositCandidate,
            ProductCandidate savingsCandidate,
            LocalDate evaluationDate
    ) {
        long savingsCapacity = Long.MAX_VALUE;
        if (savingsCandidate.getMonthlyMaxAmount() != null) {
            int contributionMonths = savingsContributionMonths(
                    tranches,
                    evaluationDate,
                    savingsCandidate.getMinMonth(),
                    savingsCandidate.getMaxMonth()
            );
            savingsCapacity = safeMultiply(
                    savingsCandidate.getMonthlyMaxAmount(),
                    contributionMonths
            );
        }
        long depositProjectedValue = projectedCandidateValue(
                depositCandidate,
                principal,
                tranches,
                evaluationDate
        );
        long savingsProjectedValue = projectedCandidateValue(
                savingsCandidate,
                principal,
                tranches,
                evaluationDate
        );
        return PortfolioPolicy.allocateByEffectiveReturn(
                principal,
                ratios,
                savingsCapacity,
                depositProjectedValue,
                savingsProjectedValue
        );
    }

    /**
     * 적금 배분액을 월 납입액으로 환산할 때 사용하는 총 납입 개월 수다.
     * 각 증여 회차에서 최초로 가입하는 적금 계약기간만 합산한다.
     * 재가입은 첫 계약의 만기금으로 운용하므로 신규 원금의 납입 한도를
     * 늘리는 기간으로 중복 계산하지 않는다.
     */
    private int savingsContributionMonths(
            List<SimulationTrancheRecord> tranches,
            LocalDate evaluationDate,
            Integer minimumContractMonths,
            Integer maximumContractMonths
    ) {
        long totalMonths = 0;
        for (SimulationTrancheRecord tranche : safeList(tranches)) {
            if (tranche.getGiftDate() == null
                    || tranche.getGiftDate().isAfter(evaluationDate)
                    || value(tranche.getInvestmentAmount()) <= 0) {
                continue;
            }
            int remainingMonths = calculator.remainingMonths(
                    tranche.getGiftDate(),
                    evaluationDate
            );
            int firstContractMonths = calculator.reinvestmentPlan(
                            remainingMonths,
                            minimumContractMonths,
                            maximumContractMonths
                    ).contractPeriods().stream()
                    .findFirst()
                    .orElse(0);
            totalMonths += firstContractMonths;
        }
        return (int) Math.min(Integer.MAX_VALUE, totalMonths);
    }

    private boolean supportsSavingsAllocation(
            ProductCandidate candidate,
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            LocalDate evaluationDate
    ) {
        if (allocatedAmount <= 0) {
            return true;
        }
        int contributionMonths = savingsContributionMonths(
                tranches,
                evaluationDate,
                candidate.getMinMonth(),
                candidate.getMaxMonth()
        );
        if (contributionMonths <= 0) {
            return false;
        }
        long monthlyContribution = divideCeiling(
                allocatedAmount,
                contributionMonths
        );
        return (candidate.getMonthlyMinAmount() == null
                || monthlyContribution >= candidate.getMonthlyMinAmount())
                && (candidate.getMonthlyMaxAmount() == null
                || monthlyContribution <= candidate.getMonthlyMaxAmount());
    }

    private long projectedCandidateValue(
            ProductCandidate candidate,
            long principal,
            List<SimulationTrancheRecord> tranches,
            LocalDate evaluationDate
    ) {
        SimulationProductRecord product = new SimulationProductRecord();
        product.setProductType(candidate.getProductType());
        product.setAppliedAnnualRatePercent(candidate.getAppliedAnnualRatePercent());
        product.setMinimumContractMonths(candidate.getMinMonth());
        product.setMaximumContractMonths(candidate.getMaxMonth());
        product.setBaseRateTiers(candidate.getBaseRateTiers());
        return calculateSelectedProductValue(
                product,
                principal,
                tranches,
                principal,
                evaluationDate,
                List.of()
        );
    }

    private SimulationProductRecord toSnapshot(
            Long portfolioId,
            ProductCandidate candidate,
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate investmentEndDate
    ) {
        SimulationProductRecord product = new SimulationProductRecord();
        product.setPortfolioId(portfolioId);
        product.setProductVersionId(candidate.getProductVersionId());
        product.setProductId(candidate.getProductId());
        product.setProductCode(candidate.getProductCode());
        product.setProductName(candidate.getProductName());
        product.setProductType(candidate.getProductType());
        product.setProductCategory(candidate.getProductCategory());
        product.setSelected(false);
        product.setAllocatedAmount(allocatedAmount);
        product.setBaseAnnualRatePercent(candidate.getBaseAnnualRatePercent());
        product.setMaximumAnnualRatePercent(candidate.getMaximumAnnualRatePercent());
        product.setAppliedAnnualRatePercent(candidate.getAppliedAnnualRatePercent());
        product.setMinimumContractMonths(candidate.getMinMonth());
        product.setMaximumContractMonths(candidate.getMaxMonth());
        product.setBaseRateTiers(candidate.getBaseRateTiers());
        long futureValue = investmentPrincipal <= 0 ? 0
                : calculateSelectedProductValue(
                product,
                allocatedAmount,
                tranches,
                investmentPrincipal,
                investmentEndDate,
                List.of()
        );
        product.setExpectedFutureValue(futureValue);
        return product;
    }

    private ProductSelectionPlan validateProductSelections(
            SimulationSaveRequest request,
            SimulationRecord simulation,
            SimulationPortfolioRecord portfolio,
            List<SimulationProductRecord> candidates,
            List<SimulationTrancheRecord> tranches,
            LocalDate evaluationDate
    ) {
        Map<Long, SimulationProductRecord> byId = candidates.stream()
                .collect(Collectors.toMap(
                        SimulationProductRecord::getSimulationProductId,
                        Function.identity()
                ));
        Set<Long> ids = new HashSet<>();
        Set<ProductType> types = EnumSet.noneOf(ProductType.class);
        List<SelectedProductPlan> plans = new ArrayList<>();
        for (SimulationSaveRequest.ProductSelection selected :
                request.getProductSelections()) {
            if (!ids.add(selected.getSimulationProductId())) {
                throw new SimulationException(
                        SimulationError.DUPLICATE_PRODUCT_SELECTION);
            }
            SimulationProductRecord product = byId.get(
                    selected.getSimulationProductId()
            );
            if (product == null) {
                throw new SimulationException(
                        SimulationError.PRODUCT_NOT_IN_PORTFOLIO);
            }
            if (!types.add(product.getProductType())) {
                throw new SimulationException(SimulationError.DUPLICATE_PRODUCT_TYPE);
            }
            List<String> conditionCodes = selected.getPreferentialConditionCodes() == null
                    ? List.of() : selected.getPreferentialConditionCodes();
            if (conditionCodes.size() != new HashSet<>(conditionCodes).size()) {
                throw new SimulationException(
                        SimulationError.INVALID_PREFERENTIAL_CONDITION);
            }
            if (product.getProductType() == ProductType.ETF
                    && !conditionCodes.isEmpty()) {
                throw new SimulationException(
                        SimulationError.ETF_PREFERENTIAL_CONDITION_NOT_ALLOWED);
            }
            List<PreferentialRateRecord> rates = conditionCodes.isEmpty()
                    ? List.of()
                    : safeList(simulationMapper.selectPreferentialRatesByCodes(
                    product.getProductVersionId(),
                    conditionCodes
            ));
            if (rates.size() != conditionCodes.size()) {
                throw new SimulationException(
                        SimulationError.INVALID_PREFERENTIAL_CONDITION);
            }
            validateProductLimits(
                    product,
                    simulation.getProductDataVersionId(),
                    simulation.getInvestmentPeriodMonths(),
                    tranches,
                    evaluationDate
            );
            plans.add(new SelectedProductPlan(product, rates));
        }

        Set<ProductType> required = EnumSet.noneOf(ProductType.class);
        if (portfolio.getDepositAmount() > 0) {
            required.add(ProductType.DEPOSIT);
        }
        if (portfolio.getSavingsAmount() > 0) {
            required.add(ProductType.SAVINGS);
        }
        if (portfolio.getEtfAmount() > 0) {
            required.add(ProductType.ETF);
        }
        if (!types.equals(required)) {
            throw new SimulationException(
                    SimulationError.PRODUCT_TYPE_SELECTION_INCOMPLETE);
        }
        for (SelectedProductPlan plan : plans) {
            long expected = switch (plan.product().getProductType()) {
                case DEPOSIT -> portfolio.getDepositAmount();
                case SAVINGS -> portfolio.getSavingsAmount();
                case ETF -> portfolio.getEtfAmount();
            };
            if (plan.product().getAllocatedAmount() != expected) {
                throw new SimulationException(
                        SimulationError.PORTFOLIO_ALLOCATION_MISMATCH);
            }
        }
        return new ProductSelectionPlan(plans);
    }

    private void validateProductLimits(
            SimulationProductRecord product,
            Long productDataVersionId,
            int investmentPeriodMonths,
            List<SimulationTrancheRecord> tranches,
            LocalDate evaluationDate
    ) {
        ProductVersionDetailRecord detail =
                simulationMapper.selectProductVersionDetail(product.getProductVersionId());
        if (detail == null || !Objects.equals(
                detail.getProductDataVersionId(), productDataVersionId)) {
            throw new SimulationException(
                    SimulationError.PRODUCT_DATA_VERSION_MISMATCH);
        }
        if (product.getProductType() != ProductType.ETF
                && !calculator.reinvestmentPlan(
                investmentPeriodMonths,
                detail.getMinimumMonths(),
                detail.getMaximumMonths()
        ).hasInvestmentContract()) {
            throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
        }
        if (product.getProductType() == ProductType.DEPOSIT) {
            if (detail.getMinimumAmount() != null
                    && product.getAllocatedAmount() < detail.getMinimumAmount()) {
                throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
            }
            if (detail.getMaximumAmount() != null
                    && product.getAllocatedAmount() > detail.getMaximumAmount()) {
                throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
            }
        }
        if (product.getProductType() == ProductType.SAVINGS) {
            int contributionMonths = savingsContributionMonths(
                    tranches,
                    evaluationDate,
                    detail.getMinimumMonths(),
                    detail.getMaximumMonths()
            );
            if (contributionMonths <= 0) {
                throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
            }
            long monthly = divideCeiling(
                    product.getAllocatedAmount(),
                    contributionMonths
            );
            if (detail.getMonthlyMinimumAmount() != null
                    && monthly < detail.getMonthlyMinimumAmount()) {
                throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
            }
            if (detail.getMonthlyMaximumAmount() != null
                    && monthly > detail.getMonthlyMaximumAmount()) {
                throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
            }
            product.setMonthlyContributionAmount(monthly);
        }
    }

    private BigDecimal representativeAppliedRate(
            SimulationProductRecord product,
            List<PreferentialRateRecord> rates,
            List<SimulationTrancheRecord> tranches,
            LocalDate investmentEndDate
    ) {
        if (product.getProductType() == ProductType.ETF) {
            return product.getBaseAnnualRatePercent();
        }
        int firstContractMonths = safeList(tranches).stream()
                .filter(tranche -> tranche.getGiftDate() != null
                        && !tranche.getGiftDate().isAfter(investmentEndDate)
                        && value(tranche.getInvestmentAmount()) > 0)
                .mapToInt(tranche -> calculator.remainingMonths(
                        tranche.getGiftDate(),
                        investmentEndDate
                ))
                .filter(months -> months > 0)
                .mapToObj(months -> calculator.reinvestmentPlan(
                        months,
                        product.getMinimumContractMonths(),
                        product.getMaximumContractMonths()
                ).contractPeriods())
                .filter(periods -> !periods.isEmpty())
                .mapToInt(periods -> periods.get(0))
                .findFirst()
                .orElseThrow(() -> new SimulationException(
                        SimulationError.PRODUCT_LIMIT_EXCEEDED));
        return appliedRateForContract(product, rates, firstContractMonths);
    }

    private long calculateSelectedProductValue(
            SimulationProductRecord product,
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate investmentEndDate,
            List<PreferentialRateRecord> preferentialRates
    ) {
        if (product.getProductType() == ProductType.ETF) {
            return calculator.calculateSelectedProductValue(
                    product,
                    allocatedAmount,
                    tranches,
                    investmentPrincipal,
                    investmentEndDate
            );
        }
        hydrateBaseRateTiers(product);
        return calculator.calculateSelectedProductValue(
                product,
                allocatedAmount,
                tranches,
                investmentPrincipal,
                investmentEndDate,
                contractMonths -> appliedRateForContract(
                        product,
                        preferentialRates,
                        contractMonths
                )
        );
    }

    private BigDecimal appliedRateForContract(
            SimulationProductRecord product,
            List<PreferentialRateRecord> preferentialRates,
            int contractMonths
    ) {
        BaseRateRecord tier = requiredRateTier(
                product.getBaseRateTiers(),
                contractMonths
        );
        BigDecimal additional = safeList(preferentialRates).stream()
                .map(PreferentialRateRecord::getAdditionalRatePercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal calculated = tier.getBaseRatePercent().add(additional);
        BigDecimal maximum = tier.getMaximumRatePercent();
        return maximum == null || calculated.compareTo(maximum) <= 0
                ? calculated : maximum;
    }

    private BaseRateRecord requiredRateTier(
            List<BaseRateRecord> tiers,
            int contractMonths
    ) {
        return safeList(tiers).stream()
                .filter(tier -> tier.getMinimumMonths() != null
                        && tier.getMinimumMonths() <= contractMonths)
                .filter(tier -> tier.getMaximumMonths() == null
                        || contractMonths <= tier.getMaximumMonths())
                .sorted(Comparator
                        .comparing(BaseRateRecord::getMinimumMonths)
                        .reversed()
                        .thenComparing(
                                tier -> tier.getMaximumMonths() == null
                                        ? Integer.MAX_VALUE : tier.getMaximumMonths()
                        ))
                .findFirst()
                .orElseThrow(() -> new SimulationException(
                        SimulationError.PRODUCT_DATA_NOT_READY));
    }

    private void hydrateBaseRateTiers(SimulationProductRecord product) {
        if (product.getProductType() == ProductType.ETF
                || (product.getBaseRateTiers() != null
                && !product.getBaseRateTiers().isEmpty())) {
            return;
        }
        List<BaseRateRecord> tiers = safeList(
                simulationMapper.selectBaseRates(product.getProductVersionId()));
        if (tiers.isEmpty()) {
            throw new SimulationException(SimulationError.PRODUCT_DATA_NOT_READY);
        }
        product.setBaseRateTiers(tiers);
    }

    private void restoreSimulationProducts(SimulationRecord simulation) {
        List<SimulationPortfolioRecord> portfolios =
                safeList(simulationMapper.selectPortfolios(simulation.getSimulationId()));
        Map<Long, SimulationResultRecord> results =
                safeList(simulationMapper.selectResults(simulation.getSimulationId())).stream()
                        .collect(Collectors.toMap(
                                SimulationResultRecord::getResultId,
                                Function.identity()
                        ));
        Map<Long, List<SimulationTrancheRecord>> tranches =
                safeList(simulationMapper.selectTranches(simulation.getSimulationId())).stream()
                        .collect(Collectors.groupingBy(
                                SimulationTrancheRecord::getResultId
                        ));
        List<SimulationResultRecord> resultList = new ArrayList<>(results.values());
        List<SimulationTrancheRecord> trancheList = tranches.values().stream()
                .flatMap(List::stream)
                .toList();
        LocalDate evaluationDate = resolveEvaluationDate(
                simulation,
                resultList,
                trancheList
        );
        for (SimulationPortfolioRecord portfolio : portfolios) {
            SimulationResultRecord result = results.get(portfolio.getResultId());
            for (SimulationProductRecord product :
                    safeList(simulationMapper.selectPortfolioProducts(
                            portfolio.getPortfolioId()
                    ))) {
                hydrateBaseRateTiers(product);
                BigDecimal baseRate = representativeAppliedRate(
                        product,
                        List.of(),
                        tranches.getOrDefault(result.getResultId(), List.of()),
                        evaluationDate
                );
                product.setAppliedAnnualRatePercent(baseRate);
                long value = calculateSelectedProductValue(
                        product,
                        product.getAllocatedAmount(),
                        tranches.getOrDefault(result.getResultId(), List.of()),
                        result.getInvestmentPrincipal(),
                        evaluationDate,
                        List.of()
                );
                int restored = simulationMapper.restoreSimulationProduct(
                        product.getSimulationProductId(),
                        baseRate,
                        value
                );
                if (restored != 1) {
                    throw new SimulationException(
                            SimulationError.SIMULATION_SAVE_FAILED);
                }
            }
        }
    }

    private SimulationResponse.Portfolio toPortfolioResponse(
            SimulationPortfolioRecord portfolio,
            SimulationResultRecord result,
            List<SimulationProductRecord> products,
            List<SimulationTrancheRecord> tranches,
            SimulationRecord simulation,
            LocalDate evaluationDate,
            Map<Long, EtfVolatilityResponse> volatilityByProductId
    ) {
        return new SimulationResponse.Portfolio(
                portfolio.getPortfolioId(),
                portfolio.getPortfolioType(),
                new SimulationResponse.Allocation(
                        portfolio.getDepositAmount(),
                        portfolio.getSavingsAmount(),
                        portfolio.getEtfAmount()
                ),
                portfolio.getExpectedFutureValue(),
                portfolio.getExpectedFutureValue() - result.getInvestmentPrincipal(),
                portfolio.isRecommended(),
                Objects.equals(
                        portfolio.getPortfolioId(),
                        simulation.getSelectedPortfolioId()
                ),
                products.stream()
                        .map(product -> toProductResponse(
                                product,
                                result.getInvestmentPrincipal(),
                                tranches,
                                simulation,
                                evaluationDate,
                                volatilityByProductId
                        ))
                        .toList()
        );
    }

    private SimulationResponse.Product toProductResponse(
            SimulationProductRecord product,
            long investmentPrincipal,
            List<SimulationTrancheRecord> tranches,
            SimulationRecord simulation,
            LocalDate evaluationDate,
            Map<Long, EtfVolatilityResponse> volatilityByProductId
    ) {
        hydrateBaseRateTiers(product);
        BigDecimal ratio = investmentPrincipal <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(product.getAllocatedAmount())
                .multiply(ONE_HUNDRED)
                .divide(
                        BigDecimal.valueOf(investmentPrincipal),
                        2,
                        RoundingMode.HALF_UP
                );
        Long monthlyContribution = product.getProductType() == ProductType.SAVINGS
                ? divideCeiling(
                product.getAllocatedAmount(),
                Math.max(1, savingsContributionMonths(
                        tranches,
                        evaluationDate,
                        product.getMinimumContractMonths(),
                        product.getMaximumContractMonths()
                ))
        ) : null;
        SimulationResponse.ReturnMetric metric =
                product.getProductType() == ProductType.ETF
                        ? new SimulationResponse.ReturnMetric(
                        "ANNUALIZED_RETURN_10Y",
                        null,
                        null,
                        product.getBaseAnnualRatePercent()
                )
                        : new SimulationResponse.ReturnMetric(
                        "INTEREST_RATE_RANGE",
                        product.getBaseAnnualRatePercent(),
                        product.getMaximumAnnualRatePercent(),
                        null
                );
        List<SimulationResponse.SelectedPreferentialCondition> conditions =
                safeList(product.getSelectedPreferentialConditions()).stream()
                        .map(item -> new SimulationResponse.SelectedPreferentialCondition(
                                item.getConditionCode(),
                                item.getAdditionalRatePercent()
                        ))
                        .toList();
        return new SimulationResponse.Product(
                product.getSimulationProductId(),
                product.getProductVersionId(),
                product.getProductId(),
                product.getProductName(),
                product.getProductType(),
                product.getAllocatedAmount(),
                monthlyContribution,
                ratio,
                product.getAppliedAnnualRatePercent(),
                product.calculationType(),
                metric,
                product.getProductType() == ProductType.ETF
                        ? product.getRiskLevel() : null,
                product.getProductType() == ProductType.ETF
                        ? volatilityByProductId.get(product.getProductId()) : null,
                product.getExpectedFutureValue(),
                product.getExpectedFutureValue() - product.getAllocatedAmount(),
                product.isSelected(),
                product.getMinimumContractMonths(),
                product.getMaximumContractMonths(),
                contractRateSchedule(
                        product,
                        tranches,
                        evaluationDate
                ),
                reinvestmentSchedule(
                        product,
                        tranches,
                        evaluationDate
                ),
                cashHoldingSchedule(
                        product,
                        tranches,
                        investmentPrincipal,
                        evaluationDate
                ),
                conditions
        );
    }

    private SimulationResponse.Selection toSelection(
            SimulationPortfolioRecord portfolio,
            SimulationResultRecord result,
            List<SimulationProductRecord> selectedProducts,
            List<SimulationTrancheRecord> tranches,
            SimulationRecord simulation,
            LocalDate evaluationDate,
            Map<Long, EtfVolatilityResponse> volatilityByProductId
    ) {
        long futureValue = selectedProducts.stream()
                .mapToLong(item -> value(item.getExpectedFutureValue()))
                .sum();
        return new SimulationResponse.Selection(
                portfolio.getPortfolioId(),
                portfolio.getPortfolioType(),
                result.getResultId(),
                result.getScenarioType(),
                result.getGiftTax(),
                result.getDonorRequiredAmount(),
                result.getInvestmentPrincipal(),
                new SimulationResponse.Allocation(
                        portfolio.getDepositAmount(),
                        portfolio.getSavingsAmount(),
                        portfolio.getEtfAmount()
                ),
                futureValue,
                futureValue - result.getInvestmentPrincipal(),
                selectedProducts.stream()
                        .map(product -> toProductResponse(
                                product,
                                result.getInvestmentPrincipal(),
                                 tranches,
                                 simulation,
                                 evaluationDate,
                                 volatilityByProductId
                        ))
                        .toList()
        );
    }

    private List<SimulationResponse.Reinvestment> reinvestmentSchedule(
            SimulationProductRecord product,
            List<SimulationTrancheRecord> tranches,
            LocalDate investmentEndDate
    ) {
        if (product.getProductType() == ProductType.ETF) {
            return List.of();
        }

        List<SimulationResponse.Reinvestment> schedule = new ArrayList<>();
        for (SimulationTrancheRecord tranche : safeList(tranches)) {
            if (tranche.getGiftDate() == null
                    || tranche.getGiftDate().isAfter(investmentEndDate)
                    || value(tranche.getInvestmentAmount()) <= 0) {
                continue;
            }
            int totalMonths = calculator.remainingMonths(
                    tranche.getGiftDate(),
                    investmentEndDate
            );
            List<Integer> periods = calculator.reinvestmentPlan(
                    totalMonths,
                    product.getMinimumContractMonths(),
                    product.getMaximumContractMonths()
            ).contractPeriods();
            LocalDate renewalDate = tranche.getGiftDate();
            for (int index = 0; index < periods.size() - 1; index++) {
                int completedMonths = periods.get(index);
                renewalDate = renewalDate.plusMonths(completedMonths);
                schedule.add(new SimulationResponse.Reinvestment(
                        tranche.getSequenceNo(),
                        index + 1,
                        renewalDate,
                        completedMonths
                ));
            }
        }
        return List.copyOf(schedule);
    }

    private List<SimulationResponse.CashHolding> cashHoldingSchedule(
            SimulationProductRecord product,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate evaluationDate
    ) {
        if (product.getProductType() == ProductType.ETF
                || product.getAllocatedAmount() == null
                || value(product.getAllocatedAmount()) <= 0
                || investmentPrincipal <= 0) {
            return List.of();
        }

        List<SimulationTrancheRecord> safeTranches = safeList(tranches);
        List<Long> portions = calculator.splitAllocatedAmountAcrossTranches(
                product.getAllocatedAmount(),
                safeTranches,
                investmentPrincipal
        );
        List<SimulationResponse.CashHolding> schedule = new ArrayList<>();
        for (int index = 0; index < safeTranches.size(); index++) {
            SimulationTrancheRecord tranche = safeTranches.get(index);
            if (tranche.getGiftDate() == null
                    || tranche.getGiftDate().isAfter(evaluationDate)
                    || value(tranche.getInvestmentAmount()) <= 0
                    || portions.get(index) <= 0) {
                continue;
            }
            int totalMonths = calculator.remainingMonths(
                    tranche.getGiftDate(),
                    evaluationDate
            );
            SimulationCalculator.ReinvestmentPlan plan = calculator.reinvestmentPlan(
                    totalMonths,
                    product.getMinimumContractMonths(),
                    product.getMaximumContractMonths()
            );
            if (plan.cashHoldingMonths() <= 0) {
                continue;
            }

            long holdingAmount = portions.get(index);
            if (plan.investedMonths() > 0) {
                holdingAmount = calculator.calculateReinvestedProductFutureValue(
                        product.calculationType(),
                        holdingAmount,
                        plan.investedMonths(),
                        product.getMinimumContractMonths(),
                        product.getMaximumContractMonths(),
                        contractMonths -> appliedRateForContract(
                                product,
                                safeList(product.getSelectedPreferentialConditions()),
                                contractMonths
                        )
                );
            }
            LocalDate holdingStartDate = tranche.getGiftDate()
                    .plusMonths(plan.investedMonths());
            schedule.add(new SimulationResponse.CashHolding(
                    tranche.getSequenceNo(),
                    holdingStartDate,
                    evaluationDate,
                    plan.cashHoldingMonths(),
                    holdingAmount,
                    "MINIMUM_CONTRACT_PERIOD_NOT_MET"
            ));
        }
        return List.copyOf(schedule);
    }

    private SimulationResponse.Tranche toTrancheResponse(
            SimulationTrancheRecord tranche
    ) {
        return new SimulationResponse.Tranche(
                tranche.getTrancheId(),
                tranche.getSequenceNo(),
                tranche.getGiftDate(),
                tranche.getGiftAmount(),
                tranche.getEstimatedGiftTax(),
                tranche.getDonorRequiredAmount(),
                tranche.getInvestmentAmount()
        );
    }

    private SimulationResponse.FrontendCalculationPolicy frontendPolicy(int months) {
        Map<ProductType, com.example.project.simulation.domain.CalculationType> methods =
                new EnumMap<>(ProductType.class);
        methods.put(
                ProductType.DEPOSIT,
                com.example.project.simulation.domain.CalculationType.SIMPLE_INTEREST
        );
        methods.put(
                ProductType.SAVINGS,
                com.example.project.simulation.domain.CalculationType.MONTHLY_INSTALLMENT
        );
        methods.put(
                ProductType.ETF,
                com.example.project.simulation.domain.CalculationType.COMPOUND_RETURN
        );
        return new SimulationResponse.FrontendCalculationPolicy(
                FORMULA_VERSION,
                "PERCENT",
                "FLOOR_TO_WON",
                methods,
                "END_OF_MONTH",
                "ANNUALIZED_RETURN_10Y",
                new SimulationResponse.ReinvestmentPolicy(
                        true,
                        true,
                        "REINVEST_PRINCIPAL_AND_INTEREST"
                ),
                PortfolioPolicy.allocations(months)
        );
    }

    private void validateSnapshotCompleteness(
            SimulationRecord simulation,
            List<SimulationResultRecord> results,
            List<SimulationTrancheRecord> tranches,
            List<SimulationPortfolioRecord> portfolios,
            List<SimulationProductRecord> products,
            ProductDataVersionRecord productDataVersion
    ) {
        if (productDataVersion == null || results.isEmpty()
                || tranches.isEmpty() || portfolios.isEmpty() || products.isEmpty()) {
            throw incompleteSnapshot();
        }

        Set<ScenarioType> scenarioTypes = results.stream()
                .map(SimulationResultRecord::getScenarioType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> resultIds = results.stream()
                .map(SimulationResultRecord::getResultId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> portfolioIds = portfolios.stream()
                .map(SimulationPortfolioRecord::getPortfolioId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!scenarioTypes.equals(EnumSet.allOf(ScenarioType.class))
                || results.size() != ScenarioType.values().length
                || resultIds.size() != results.size()
                || portfolioIds.size() != portfolios.size()
                || tranches.stream().anyMatch(item ->
                item.getResultId() == null || !resultIds.contains(item.getResultId()))
                || portfolios.stream().anyMatch(item ->
                item.getResultId() == null
                        || !resultIds.contains(item.getResultId())
                        || item.getPortfolioType() == null
                        || item.getScenarioType() == null)
                || products.stream().anyMatch(item ->
                item.getSimulationProductId() == null
                        || item.getPortfolioId() == null
                        || !portfolioIds.contains(item.getPortfolioId())
                        || item.getProductVersionId() == null
                        || item.getProductType() == null
                        || item.getAllocatedAmount() == null
                        || item.getAppliedAnnualRatePercent() == null
                        || item.getExpectedFutureValue() == null)) {
            throw incompleteSnapshot();
        }

        for (SimulationResultRecord result : results) {
            List<SimulationTrancheRecord> resultTranches = tranches.stream()
                    .filter(item -> Objects.equals(item.getResultId(), result.getResultId()))
                    .sorted(Comparator.comparing(
                            SimulationTrancheRecord::getSequenceNo,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .toList();
            List<SimulationPortfolioRecord> resultPortfolios = portfolios.stream()
                    .filter(item -> Objects.equals(item.getResultId(), result.getResultId()))
                    .toList();
            Set<RiskProfile> profiles = resultPortfolios.stream()
                    .map(SimulationPortfolioRecord::getPortfolioType)
                    .collect(Collectors.toSet());
            boolean allocationMismatch = resultPortfolios.stream()
                    .anyMatch(item -> item.getDepositAmount() == null
                            || item.getSavingsAmount() == null
                            || item.getEtfAmount() == null
                            || item.getDepositAmount() < 0
                            || item.getSavingsAmount() < 0
                            || item.getEtfAmount() < 0
                            || item.getDepositAmount()
                            + item.getSavingsAmount()
                            + item.getEtfAmount()
                            != value(result.getInvestmentPrincipal()));
            if (!validTranches(simulation, result, resultTranches)
                    || !profiles.equals(EnumSet.allOf(RiskProfile.class))
                    || resultPortfolios.size() != RiskProfile.values().length
                    || resultPortfolios.stream().anyMatch(item ->
                    item.getScenarioType() != result.getScenarioType())
                    || allocationMismatch) {
                throw incompleteSnapshot();
            }
            for (SimulationPortfolioRecord portfolio : resultPortfolios) {
                List<SimulationProductRecord> portfolioProducts = products.stream()
                        .filter(item -> Objects.equals(
                                item.getPortfolioId(), portfolio.getPortfolioId()))
                        .toList();
                if (!validPortfolioProducts(portfolio, portfolioProducts)) {
                    throw incompleteSnapshot();
                }
            }
        }

        for (RiskProfile profile : RiskProfile.values()) {
            long recommendedCount = portfolios.stream()
                    .filter(item -> item.getPortfolioType() == profile)
                    .filter(SimulationPortfolioRecord::isRecommended)
                    .count();
            if (recommendedCount != 1) {
                throw new SimulationException(
                        SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE);
            }
        }
    }

    private boolean validTranches(
            SimulationRecord simulation,
            SimulationResultRecord result,
            List<SimulationTrancheRecord> tranches
    ) {
        if (result.getScenarioType() == null
                || result.getDeductionAmount() == null
                || result.getTaxableAmount() == null
                || result.getGiftTax() == null
                || result.getDonorRequiredAmount() == null
                || result.getPostTaxAmount() == null
                || result.getInvestmentPrincipal() == null
                || tranches.isEmpty()) {
            return false;
        }

        LocalDate previousDate = null;
        for (int index = 0; index < tranches.size(); index++) {
            SimulationTrancheRecord tranche = tranches.get(index);
            if (tranche.getTrancheId() == null
                    || !Objects.equals(tranche.getSequenceNo(), index + 1)
                    || tranche.getGiftDate() == null
                    || tranche.getGiftDate().isBefore(simulation.getGiftDate())
                    || previousDate != null && tranche.getGiftDate().isBefore(previousDate)
                    || value(tranche.getGiftAmount()) <= 0
                    || tranche.getEstimatedGiftTax() == null
                    || tranche.getEstimatedGiftTax() < 0
                    || tranche.getDonorRequiredAmount() == null
                    || tranche.getDonorRequiredAmount() < 0
                    || tranche.getInvestmentAmount() == null
                    || tranche.getInvestmentAmount() < 0) {
                return false;
            }
            previousDate = tranche.getGiftDate();
        }

        long giftAmount = tranches.stream()
                .mapToLong(item -> value(item.getGiftAmount()))
                .sum();
        long giftTax = tranches.stream()
                .mapToLong(item -> value(item.getEstimatedGiftTax()))
                .sum();
        long donorRequiredAmount = tranches.stream()
                .mapToLong(item -> value(item.getDonorRequiredAmount()))
                .sum();
        long investmentPrincipal = tranches.stream()
                .mapToLong(item -> value(item.getInvestmentAmount()))
                .sum();
        long expectedPostTaxAmount =
                simulation.getTaxPaymentMethod()
                        == TaxPaymentMethod.DONOR_PAYS
                        ? simulation.getRequestedAmount()
                        : Math.max(0, simulation.getRequestedAmount() - result.getGiftTax());
        boolean immediateScheduleValid =
                result.getScenarioType() != ScenarioType.IMMEDIATE
                        || tranches.size() == 1
                        && Objects.equals(
                        tranches.get(0).getGiftDate(), simulation.getGiftDate())
                        && Objects.equals(
                        tranches.get(0).getGiftAmount(), simulation.getRequestedAmount());

        return giftAmount == value(simulation.getRequestedAmount())
                && giftTax == value(result.getGiftTax())
                && donorRequiredAmount == value(result.getDonorRequiredAmount())
                && investmentPrincipal == value(result.getInvestmentPrincipal())
                && expectedPostTaxAmount == value(result.getPostTaxAmount())
                && immediateScheduleValid;
    }

    private boolean validPortfolioProducts(
            SimulationPortfolioRecord portfolio,
            List<SimulationProductRecord> products
    ) {
        Map<ProductType, Long> allocations = portfolioAllocations(portfolio);
        Set<ProductType> requiredTypes = allocations.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Map<ProductType, List<SimulationProductRecord>> productsByType = products.stream()
                .collect(Collectors.groupingBy(
                        SimulationProductRecord::getProductType,
                        () -> new EnumMap<>(ProductType.class),
                        Collectors.toList()
                ));
        if (!productsByType.keySet().equals(requiredTypes)) {
            return false;
        }

        Set<Long> productVersionIds = new HashSet<>();
        for (Map.Entry<ProductType, List<SimulationProductRecord>> entry :
                productsByType.entrySet()) {
            List<SimulationProductRecord> candidates = entry.getValue();
            long expectedAllocation = allocations.get(entry.getKey());
            if (candidates.isEmpty() || candidates.size() > MAX_PRODUCT_CANDIDATES
                    || candidates.stream().anyMatch(product ->
                    !productVersionIds.add(product.getProductVersionId())
                            || value(product.getAllocatedAmount()) != expectedAllocation
                            || value(product.getExpectedFutureValue()) < 0)) {
                return false;
            }
        }
        return true;
    }

    private void validateSavedSelection(
            SimulationPortfolioRecord selectedPortfolio,
            SimulationResultRecord selectedResult,
            List<SimulationProductRecord> selectedProducts,
            List<SimulationProductRecord> allProducts
    ) {
        if (selectedResult == null
                || !selectedPortfolio.isRecommended()
                || selectedProducts.isEmpty()
                || allProducts.stream().anyMatch(item ->
                item.isSelected()
                        && !Objects.equals(
                        item.getPortfolioId(), selectedPortfolio.getPortfolioId()))) {
            throw incompleteSavedSelection();
        }

        Map<ProductType, Long> allocations = portfolioAllocations(selectedPortfolio);
        Set<ProductType> requiredTypes = allocations.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<ProductType> selectedTypes = selectedProducts.stream()
                .map(SimulationProductRecord::getProductType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long selectedAmount = selectedProducts.stream()
                .mapToLong(item -> value(item.getAllocatedAmount()))
                .sum();
        boolean allocationMismatch = selectedProducts.stream().anyMatch(item ->
                item.getProductType() == null
                        || !Objects.equals(
                        item.getAllocatedAmount(), allocations.get(item.getProductType())));
        if (!selectedTypes.equals(requiredTypes)
                || selectedTypes.size() != selectedProducts.size()
                || allocationMismatch
                || selectedAmount != value(selectedResult.getInvestmentPrincipal())) {
            throw incompleteSavedSelection();
        }
    }

    private Map<ProductType, Long> portfolioAllocations(
            SimulationPortfolioRecord portfolio
    ) {
        Map<ProductType, Long> allocations = new EnumMap<>(ProductType.class);
        allocations.put(ProductType.DEPOSIT, value(portfolio.getDepositAmount()));
        allocations.put(ProductType.SAVINGS, value(portfolio.getSavingsAmount()));
        allocations.put(ProductType.ETF, value(portfolio.getEtfAmount()));
        return allocations;
    }

    private SimulationException incompleteSnapshot() {
        return new SimulationException(
                SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE);
    }

    private SimulationException incompleteSavedSelection() {
        return new SimulationException(
                SimulationError.SAVED_SELECTION_INCOMPLETE);
    }

    private Map<ProductType, List<ProductCandidate>> loadSafeAssetCandidates(
            Long dataVersionId,
            int months
    ) {
        Map<ProductType, List<ProductCandidate>> result =
                new EnumMap<>(ProductType.class);
        result.put(
                ProductType.DEPOSIT,
                prepareReinvestableCandidates(
                        simulationMapper.selectDepositCandidates(
                                dataVersionId,
                                months
                        ),
                        months
                )
        );
        result.put(
                ProductType.SAVINGS,
                prepareReinvestableCandidates(
                        simulationMapper.selectSavingsCandidates(
                                dataVersionId,
                                months
                        ),
                        months
                )
        );
        return result;
    }

    private List<ProductCandidate> prepareReinvestableCandidates(
            List<ProductCandidate> candidates,
            int investmentPeriodMonths
    ) {
        List<ProductCandidate> eligible = requireCandidates(candidates).stream()
                .filter(candidate -> prepareCandidateRateTiers(
                        candidate,
                        investmentPeriodMonths
                ))
                .sorted(Comparator.comparingLong((ProductCandidate candidate) ->
                        calculator.calculateReinvestedProductFutureValue(
                                candidate.calculationType(),
                                100_000_000L,
                                investmentPeriodMonths,
                                candidate.getMinMonth(),
                                candidate.getMaxMonth(),
                                contractMonths -> requiredRateTier(
                                        candidate.getBaseRateTiers(),
                                        contractMonths
                                ).getBaseRatePercent()
                        )).reversed())
                .toList();
        if (eligible.isEmpty()) {
            throw new SimulationException(
                    SimulationError.PRODUCT_CANDIDATE_NOT_FOUND);
        }
        return eligible;
    }

    private boolean prepareCandidateRateTiers(
            ProductCandidate candidate,
            int investmentPeriodMonths
    ) {
        List<Integer> periods = calculator.reinvestmentPlan(
                investmentPeriodMonths,
                candidate.getMinMonth(),
                candidate.getMaxMonth()
        ).contractPeriods();
        if (periods.isEmpty()) {
            return false;
        }
        List<BaseRateRecord> tiers = safeList(
                simulationMapper.selectBaseRates(candidate.getProductVersionId()));
        if (tiers.isEmpty()) {
            return false;
        }
        candidate.setBaseRateTiers(tiers);
        try {
            for (Integer period : periods) {
                requiredRateTier(tiers, period);
            }
            BaseRateRecord firstTier = requiredRateTier(tiers, periods.get(0));
            candidate.setBaseAnnualRatePercent(firstTier.getBaseRatePercent());
            candidate.setMaximumAnnualRatePercent(firstTier.getMaximumRatePercent());
            candidate.setAppliedAnnualRatePercent(firstTier.getBaseRatePercent());
            return true;
        } catch (SimulationException exception) {
            if (exception.getError() == SimulationError.PRODUCT_DATA_NOT_READY) {
                return false;
            }
            throw exception;
        }
    }

    private Map<RiskProfile, List<ProductCandidate>> loadEtfCandidates(
            Long dataVersionId
    ) {
        Map<RiskProfile, List<ProductCandidate>> result =
                new EnumMap<>(RiskProfile.class);
        for (RiskProfile profile : RiskProfile.values()) {
            result.put(
                    profile,
                    requireCandidates(simulationMapper.selectEtfCandidates(
                            dataVersionId,
                            profile,
                            MAX_PRODUCT_CANDIDATES
                    ))
            );
        }
        return result;
    }

    private List<ProductCandidate> requireCandidates(List<ProductCandidate> candidates) {
        List<ProductCandidate> safe = safeList(candidates);
        if (safe.isEmpty()) {
            throw new SimulationException(
                    SimulationError.PRODUCT_CANDIDATE_NOT_FOUND);
        }
        if (safe.stream().anyMatch(item -> item.getProductVersionId() == null
                || item.getProductDataVersionId() == null
                || item.getProductType() == null
                || item.getAppliedAnnualRatePercent() == null)) {
            throw new SimulationException(
                    SimulationError.PRODUCT_DATA_VERSION_INCOMPLETE);
        }
        return safe;
    }

    private FamilySnapshot requireFamily(Long familyId, Long userId) {
        FamilySnapshot family = simulationMapper.selectFamily(familyId);
        if (family == null) {
            throw new SimulationException(SimulationError.FAMILY_NOT_FOUND);
        }
        if (!Objects.equals(family.getUserId(), userId)) {
            throw new SimulationException(SimulationError.FAMILY_ACCESS_DENIED);
        }
        if (family.getFamilyNameEncrypted() != null) {
            family.setFamilyName(
                    piiProtectionService.decryptFamilyName(family.getFamilyNameEncrypted())
            );
        }
        if (family.getBirthDateEncrypted() != null) {
            family.setBirthDate(
                    piiProtectionService.decryptFamilyBirthDate(family.getBirthDateEncrypted())
            );
        }
        return family;
    }

    private SnapshotDerivedValues deriveSnapshotValues(
            SimulationRecord simulation
    ) {
        if (simulation.getAsOfDate() == null
                || simulation.getGiftDate() == null
                || simulation.getBirthDate() == null
                || simulation.getRequestedAmount() == null
                || simulation.getRequestedAmount() <= 0
                || simulation.getTaxPaymentMethod() == null
                || simulation.getPreviousGiftAmount() == null
                || simulation.getPreviousGiftAmount() < 0
                || simulation.getDeductionLimit() == null
                || simulation.getDeductionLimit() < 0) {
            throw incompleteSnapshot();
        }

        int ageAtSimulation = Period.between(
                simulation.getBirthDate(), simulation.getGiftDate()).getYears();
        long previousGiftAmount = simulation.getPreviousGiftAmount();
        long deductionLimit = simulation.getDeductionLimit();
        long usedDeductionAmount = Math.min(previousGiftAmount, deductionLimit);
        return new SnapshotDerivedValues(
                ageAtSimulation,
                ageAtSimulation < 19,
                simulation.getGiftDate().minusYears(DEDUCTION_WINDOW_YEARS),
                previousGiftAmount,
                deductionLimit,
                usedDeductionAmount,
                Math.max(0, deductionLimit - usedDeductionAmount)
        );
    }

    SimulationRecord requireSimulation(Long simulationId, Long userId) {
        SimulationRecord simulation = simulationMapper.selectSimulation(simulationId);
        if (simulation == null) {
            throw new SimulationException(SimulationError.SIMULATION_NOT_FOUND);
        }
        if (!Objects.equals(simulation.getUserId(), userId)) {
            throw new SimulationException(SimulationError.SIMULATION_ACCESS_DENIED);
        }
        revealSimulation(simulation);
        if (simulation.getStatus() == SimulationStatus.DRAFT
                && (simulation.getExpiredAt() == null
                || !simulation.getExpiredAt().isAfter(LocalDateTime.now()))) {
            throw new SimulationException(SimulationError.SIMULATION_EXPIRED);
        }
        if (simulation.getStatus() == SimulationStatus.DRAFT
                && (simulation.getSavedAt() != null
                || simulation.getSelectedPortfolioId() != null)) {
            throw incompleteSnapshot();
        }
        if (simulation.getStatus() == SimulationStatus.SAVED
                && (simulation.getSelectedPortfolioId() == null
                || simulation.getSavedAt() == null)) {
            throw incompleteSavedSelection();
        }
        return simulation;
    }

    private SimulationRecord revealSimulation(SimulationRecord simulation) {
        if (simulation.getFamilyNameEncrypted() != null) {
            simulation.setFamilyName(
                    piiProtectionService.decryptFamilyName(simulation.getFamilyNameEncrypted())
            );
        }
        if (simulation.getBirthDateEncrypted() != null) {
            simulation.setBirthDate(
                    piiProtectionService.decryptFamilyBirthDate(simulation.getBirthDateEncrypted())
            );
        }
        return simulation;
    }

    void validateUser(Long userId) {
        if (userId == null || userMapper.findById(userId) == null) {
            throw new SimulationException(SimulationError.USER_NOT_FOUND);
        }
    }

    private void validateExecuteRequest(SimulationExecuteRequest request) {
        if (request == null || request.getFamilyId() == null
                || request.getFamilyId() <= 0
                || request.getRequestedAmount() == null
                || request.getTaxPaymentMethod() == null
                || request.getInvestmentPeriodMonths() == null
                || request.getGiftDate() == null) {
            throw new SimulationException(
                    SimulationError.INVALID_SIMULATION_REQUEST);
        }
        if (request.getRequestedAmount() <= 0) {
            throw new SimulationException(
                    SimulationError.INVALID_REQUESTED_AMOUNT);
        }
        if (request.getInvestmentPeriodMonths() < 1
                || request.getInvestmentPeriodMonths() > MAX_INVESTMENT_MONTHS) {
            throw new SimulationException(
                    SimulationError.INVALID_INVESTMENT_PERIOD);
        }
        if (request.getGiftDate().isBefore(LocalDate.now())) {
            throw new SimulationException(SimulationError.INVALID_GIFT_DATE);
        }
    }

    private void validateSimulationId(Long simulationId) {
        if (simulationId == null || simulationId <= 0) {
            throw new SimulationException(SimulationError.INVALID_SIMULATION_ID);
        }
    }

    private void validateSaveRequest(
            Long simulationId,
            SimulationSaveRequest request
    ) {
        validateSimulationId(simulationId);
        if (request == null || request.getVersion() == null
                || request.getVersion() <= 0
                || request.getSelectedPortfolioId() == null
                || request.getSelectedPortfolioId() <= 0
                || request.getReplaceExistingSaved() == null
                || request.getProductSelections() == null
                || request.getProductSelections().isEmpty()) {
            throw new SimulationException(SimulationError.INVALID_SAVE_REQUEST);
        }
    }

    private void validateReplacementIntent(
            SimulationSaveRequest request,
            SimulationRecord activeSaved,
            boolean replacingAnother
    ) {
        if (replacingAnother && !Boolean.TRUE.equals(
                request.getReplaceExistingSaved())) {
            throw new SimulationException(
                    SimulationError.ACTIVE_SAVED_SIMULATION_EXISTS,
                    activeSavedSimulationData(activeSaved)
            );
        }
        if (replacingAnother
                && request.getExpectedExistingSavedSimulationId() == null) {
            throw new SimulationException(
                    SimulationError.EXISTING_SAVED_SIMULATION_ID_REQUIRED);
        }
        if (replacingAnother && !Objects.equals(
                activeSaved.getSimulationId(),
                request.getExpectedExistingSavedSimulationId()
        )) {
            throw new SimulationException(
                    SimulationError.EXISTING_SAVED_SIMULATION_CHANGED,
                    existingSavedChangedData(
                            request.getExpectedExistingSavedSimulationId(),
                            activeSaved.getSimulationId()
                    )
            );
        }
        if (!replacingAnother
                && Boolean.TRUE.equals(request.getReplaceExistingSaved())
                && request.getExpectedExistingSavedSimulationId() != null) {
            throw new SimulationException(
                    SimulationError.EXISTING_SAVED_SIMULATION_CHANGED,
                    existingSavedChangedData(
                            request.getExpectedExistingSavedSimulationId(),
                            activeSaved == null ? null : activeSaved.getSimulationId()
                    )
            );
        }
    }

    private Map<String, Object> activeSavedSimulationData(
            SimulationRecord activeSaved
    ) {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("simulationId", activeSaved.getSimulationId());
        SimulationPortfolioRecord selected = activeSaved.getSelectedPortfolioId() == null
                ? null
                : simulationMapper.selectPortfolio(activeSaved.getSelectedPortfolioId());
        saved.put("portfolioType", selected == null ? null : selected.getPortfolioType());
        saved.put("scenarioType", selected == null ? null : selected.getScenarioType());
        Long expectedFutureValue = null;
        if (selected != null) {
            List<SimulationProductRecord> selectedProducts = safeList(
                    simulationMapper.selectPortfolioProducts(selected.getPortfolioId())
            ).stream().filter(SimulationProductRecord::isSelected).toList();
            expectedFutureValue = selectedProducts.isEmpty()
                    ? selected.getExpectedFutureValue()
                    : selectedProducts.stream()
                    .map(SimulationProductRecord::getExpectedFutureValue)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .sum();
        }
        saved.put("expectedFutureValue", expectedFutureValue);
        saved.put("savedAt", activeSaved.getSavedAt());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("existingSavedSimulation", saved);
        return data;
    }

    private Map<String, Object> existingSavedChangedData(
            Long expectedSimulationId,
            Long currentSimulationId
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expectedExistingSavedSimulationId", expectedSimulationId);
        data.put("currentSavedSimulationId", currentSimulationId);
        return data;
    }

    private Map<String, Object> versionConflictData(
            Long requestedVersion,
            Long currentVersion
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestedVersion", requestedVersion);
        data.put("currentVersion", currentVersion);
        return data;
    }

    private SimulationSaveResponse.ClientServerDifference clientServerDifference(
            SimulationSaveRequest request,
            long serverFutureValue,
            long serverProfit
    ) {
        if (request.getClientCalculation() == null) {
            return null;
        }
        return new SimulationSaveResponse.ClientServerDifference(
                serverFutureValue
                        - request.getClientCalculation().getExpectedFutureValue(),
                serverProfit - request.getClientCalculation().getExpectedProfit()
        );
    }

    private List<SimulationResponse.ContractRate> contractRateSchedule(
            SimulationProductRecord product,
            List<SimulationTrancheRecord> tranches,
            LocalDate investmentEndDate
    ) {
        if (product.getProductType() == ProductType.ETF) {
            return List.of();
        }

        List<SimulationResponse.ContractRate> schedule = new ArrayList<>();
        List<PreferentialRateRecord> preferentialRates =
                safeList(product.getSelectedPreferentialConditions());
        for (SimulationTrancheRecord tranche : safeList(tranches)) {
            if (tranche.getGiftDate() == null
                    || tranche.getGiftDate().isAfter(investmentEndDate)
                    || value(tranche.getInvestmentAmount()) <= 0) {
                continue;
            }
            int totalMonths = calculator.remainingMonths(
                    tranche.getGiftDate(),
                    investmentEndDate
            );
            List<Integer> periods = calculator.reinvestmentPlan(
                    totalMonths,
                    product.getMinimumContractMonths(),
                    product.getMaximumContractMonths()
            ).contractPeriods();
            LocalDate contractStartDate = tranche.getGiftDate();
            for (int index = 0; index < periods.size(); index++) {
                int contractMonths = periods.get(index);
                LocalDate contractEndDate = contractStartDate.plusMonths(contractMonths);
                BaseRateRecord tier = requiredRateTier(
                        product.getBaseRateTiers(),
                        contractMonths
                );
                schedule.add(new SimulationResponse.ContractRate(
                        tranche.getSequenceNo(),
                        index + 1,
                        contractStartDate,
                        contractEndDate,
                        contractMonths,
                        tier.getBaseRatePercent(),
                        tier.getMaximumRatePercent(),
                        appliedRateForContract(
                                product,
                                preferentialRates,
                                contractMonths
                        )
                ));
                contractStartDate = contractEndDate;
            }
        }
        return List.copyOf(schedule);
    }

    private long resolveDeductionLimit(FamilySnapshot family, LocalDate giftDate) {
        int age = Period.between(family.getBirthDate(), giftDate).getYears();
        boolean minor = age < ADULT_AGE;
        DeductionRule rule = simulationMapper.selectDeductionRule(
                family.getRelation(),
                minor,
                giftDate
        );
        if (rule == null || rule.getDeductionLimit() == null) {
            throw new SimulationException(SimulationError.DEDUCTION_RULE_NOT_FOUND);
        }
        return rule.getDeductionLimit();
    }

    private LocalDate resolveDeductionRenewalDate(
            List<GiftHistoryRecord> gifts,
            List<SimulationTrancheRecord> plannedTranches,
            LocalDate giftDate
    ) {
        List<LocalDate> giftDates = new ArrayList<>();
        safeList(gifts).stream()
                .map(GiftHistoryRecord::getGiftDate)
                .filter(Objects::nonNull)
                .forEach(giftDates::add);
        safeList(plannedTranches).stream()
                .map(SimulationTrancheRecord::getGiftDate)
                .filter(Objects::nonNull)
                .forEach(giftDates::add);

        return giftDates.stream()
                .map(date -> date.plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1))
                .filter(date -> date.isAfter(giftDate))
                .min(LocalDate::compareTo)
                .orElse(giftDate.plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1));
    }

    private LocalDate resolveEvaluationDate(
            SimulationRecord simulation,
            List<SimulationResultRecord> results,
            List<SimulationTrancheRecord> tranches
    ) {
        // DRAFT/SAVED 상세 조회는 실행 당시 계산 정책을 그대로 재현해야 한다.
        // 하루 연장 정책 도입 전 스냅샷에는 이를 소급 적용하지 않는다.
        if (!FORMULA_VERSION.equals(simulation.getFormulaVersion())) {
            return simulation.getInvestmentEndDate();
        }
        Long optimizedResultId = safeList(results).stream()
                .filter(result -> result.getScenarioType() == ScenarioType.TAX_OPTIMIZED)
                .map(SimulationResultRecord::getResultId)
                .findFirst()
                .orElse(null);
        boolean hasOptimizedSplit = optimizedResultId != null
                && safeList(tranches).stream()
                .filter(tranche -> Objects.equals(
                        tranche.getResultId(),
                        optimizedResultId
                ))
                .count() > 1;
        return resolveEvaluationDate(
                simulation.getInvestmentEndDate(),
                simulation.getInvestmentPeriodMonths(),
                hasOptimizedSplit
        );
    }

    private LocalDate resolveEvaluationDate(
            LocalDate investmentEndDate,
            int investmentPeriodMonths,
            boolean hasOptimizedSplit
    ) {
        if (investmentEndDate == null) {
            return null;
        }
        return investmentPeriodMonths > DEDUCTION_WINDOW_YEARS * 12
                && hasOptimizedSplit
                ? investmentEndDate.plusDays(1)
                : investmentEndDate;
    }

    private LocalDate nextReleaseDate(List<GiftPoint> history, LocalDate afterDate) {
        return history.stream()
                .map(point -> point.date().plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1))
                .filter(date -> date.isAfter(afterDate))
                .min(LocalDate::compareTo)
                .orElse(afterDate.plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1));
    }

    private LocalDate nextPlanningDate(
            List<GiftPoint> history,
            LocalDate afterDate,
            LocalDate adulthoodDate
    ) {
        LocalDate nextReleaseDate = nextReleaseDate(history, afterDate);

        if (adulthoodDate != null
                && adulthoodDate.isAfter(afterDate)
                && adulthoodDate.isBefore(nextReleaseDate)) {
            return adulthoodDate;
        }
        return nextReleaseDate;
    }

    static boolean isWithinDeductionWindow(LocalDate giftDate, LocalDate calculationDate) {
        return giftDate != null
                && calculationDate != null
                && giftDate.isAfter(calculationDate.minusYears(DEDUCTION_WINDOW_YEARS))
                && !giftDate.isAfter(calculationDate);
    }

    private String executeFingerprint(
            SimulationExecuteRequest request,
            LocalDate asOfDate
    ) {
        return request.getFamilyId() + "|"
                + request.getRequestedAmount() + "|"
                + request.getTaxPaymentMethod() + "|"
                + request.getInvestmentPeriodMonths() + "|"
                + request.getGiftDate() + "|"
                + asOfDate;
    }

    private String saveFingerprint(Long simulationId, SimulationSaveRequest request) {
        List<String> products = request.getProductSelections().stream()
                .map(item -> item.getSimulationProductId() + ":"
                        + safeList(item.getPreferentialConditionCodes()).stream()
                        .sorted()
                        .collect(Collectors.joining(",")))
                .sorted()
                .toList();
        return simulationId + "|" + request.getVersion() + "|"
                + request.getSelectedPortfolioId() + "|"
                + request.getReplaceExistingSaved() + "|"
                + request.getExpectedExistingSavedSimulationId() + "|"
                + String.join(";", products) + "|"
                + (request.getClientCalculation() == null ? ""
                : request.getClientCalculation().getFormulaVersion() + ":"
                + request.getClientCalculation().getExpectedFutureValue() + ":"
                + request.getClientCalculation().getExpectedProfit());
    }

    private void rememberSaveAfterCommit(
            Long userId,
            String operation,
            String idempotencyKey,
            String fingerprint,
            SimulationSaveResponse response
    ) {
        Runnable remember = () -> idempotencyStore.remember(
                userId, operation, idempotencyKey, fingerprint, response);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            remember.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        remember.run();
                    }
                }
        );
    }

    private long divideCeiling(long amount, int divisor) {
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(divisor), 0, RoundingMode.CEILING)
                .longValue();
    }

    private long safeMultiply(long amount, int multiplier) {
        try {
            return Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private Map<Long, EtfVolatilityResponse> volatilityByProductId(
            List<SimulationProductRecord> products,
            LocalDate asOfDate
    ) {
        return safeList(products).stream()
                .filter(item -> item.getProductType() == ProductType.ETF)
                .collect(Collectors.toMap(
                        SimulationProductRecord::getProductId,
                        Function.identity(),
                        (left, right) -> left
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> volatilityCalculator.calculate(
                                safeList(simulationMapper.selectRecentEtfPrices(
                                        entry.getKey(),
                                        asOfDate,
                                        EtfVolatilityCalculator.MAX_PRICE_OBSERVATIONS
                                )),
                                entry.getValue().getRiskLevel()
                        )
                ));
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record SnapshotDerivedValues(
            int ageAtSimulation,
            boolean minorAtSimulation,
            LocalDate lookbackStartDate,
            long previousGiftAmount,
            long deductionLimit,
            long usedDeductionAmount,
            long remainingDeductionAmount
    ) {
    }

    private record ScenarioAggregate(
            SimulationResultRecord result,
            List<SimulationTrancheRecord> tranches
    ) {
    }

    private record PersistedScenario(
            SimulationResultRecord result,
            Map<RiskProfile, SimulationPortfolioRecord> portfolios
    ) {
    }

    private record GiftPoint(LocalDate date, long amount) {
    }

    private record ProductSelectionPlan(List<SelectedProductPlan> products) {
    }

    private record SelectedProductPlan(
            SimulationProductRecord product,
            List<PreferentialRateRecord> preferentialRates
    ) {
    }
}
