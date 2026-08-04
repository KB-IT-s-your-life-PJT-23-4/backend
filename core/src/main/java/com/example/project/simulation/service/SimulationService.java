package com.example.project.simulation.service;

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
import com.example.project.simulation.dto.request.SimulationExecuteRequest;
import com.example.project.simulation.dto.request.SimulationSaveRequest;
import com.example.project.simulation.dto.response.SimulationResponse;
import com.example.project.simulation.dto.response.SimulationSaveResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.mapper.SimulationMapper;
import com.example.project.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public static final String FORMULA_VERSION = "INVESTMENT_V1";
    public static final String CALCULATION_VERSION = "GIFT_SIM_V2";

    private static final int DEDUCTION_WINDOW_YEARS = 10;
    private static final int MAX_PRODUCT_CANDIDATES = 3;
    private static final int MAX_INVESTMENT_MONTHS = 240;
    private static final int DRAFT_RETENTION_HOURS = 24;
    private static final long CALCULATION_TOLERANCE_WON = 1L;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final SimulationMapper simulationMapper;
    private final UserMapper userMapper;
    private final SimulationCalculator calculator;
    private final SimulationIdempotencyStore idempotencyStore;

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
            int age = Period.between(family.getBirthDate(), asOfDate).getYears();
            boolean minor = age < 19;
            DeductionRule rule = simulationMapper.selectDeductionRule(
                    family.getRelation(),
                    minor,
                    asOfDate
            );
            if (rule == null || rule.getDeductionLimit() == null) {
                throw new SimulationException(SimulationError.DEDUCTION_RULE_NOT_FOUND);
            }

            LocalDate lookbackStart = asOfDate.minusYears(DEDUCTION_WINDOW_YEARS);
            List<GiftHistoryRecord> completedGifts = safeList(
                    simulationMapper.selectCompletedGifts(
                            family.getFamilyId(),
                            lookbackStart,
                            asOfDate
                    )
            );
            long previousGiftAmount = completedGifts.stream()
                    .mapToLong(gift -> value(gift.getAmount()))
                    .sum();
            long deductionLimit = rule.getDeductionLimit();
            long usedDeduction = Math.min(previousGiftAmount, deductionLimit);
            long remainingDeduction = Math.max(0, deductionLimit - usedDeduction);
            LocalDate renewalDate = resolveDeductionRenewalDate(completedGifts, asOfDate);

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
                    asOfDate.plusMonths(request.getInvestmentPeriodMonths());
            ScenarioAggregate immediate = immediateScenario(
                    request,
                    remainingDeduction,
                    taxBrackets,
                    asOfDate
            );
            ScenarioAggregate optimized = optimizedScenario(
                    request,
                    remainingDeduction,
                    deductionLimit,
                    taxBrackets,
                    asOfDate,
                    renewalDate,
                    completedGifts,
                    investmentEndDate
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
            simulation.setInvestmentEndDate(investmentEndDate);
            simulation.setCalculationVersion(CALCULATION_VERSION);
            simulation.setFormulaVersion(FORMULA_VERSION);
            simulation.setVersion(1L);
            simulation.setAgeAtSimulation(age);
            simulation.setMinorAtSimulation(minor);
            simulation.setLookbackStartDate(lookbackStart);
            simulation.setPreviousGiftAmount(previousGiftAmount);
            simulation.setDeductionLimit(deductionLimit);
            simulation.setUsedDeductionAmount(usedDeduction);
            simulation.setRemainingDeductionAmount(remainingDeduction);
            simulation.setDeductionRenewalDate(renewalDate);
            simulation.setCreatedAt(now);
            simulation.setUpdatedAt(now);
            simulation.setExpiredAt(now.plusHours(DRAFT_RETENTION_HOURS));
            simulationMapper.insertSimulation(simulation);

            PersistedScenario immediatePersisted = persistScenario(
                    simulation.getSimulationId(),
                    immediate,
                    safeCandidates,
                    etfCandidates,
                    request.getInvestmentPeriodMonths(),
                    investmentEndDate,
                    now
            );
            PersistedScenario optimizedPersisted = persistScenario(
                    simulation.getSimulationId(),
                    optimized,
                    safeCandidates,
                    etfCandidates,
                    request.getInvestmentPeriodMonths(),
                    investmentEndDate,
                    now
            );
            markRecommendations(immediatePersisted, optimizedPersisted);

            SimulationResponse response = get(simulation.getSimulationId(), userId);
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
            var cached = idempotencyStore.find(
                    userId,
                    "PUT:/api/gs/" + simulationId + "/save",
                    idempotencyKey,
                    fingerprint,
                    SimulationSaveResponse.class
            );
            if (cached.isPresent()) {
                return cached.get();
            }

            SimulationRecord target = requireSimulation(simulationId, userId);
            if (!Objects.equals(target.getVersion(), request.getVersion())) {
                throw new SimulationException(
                        SimulationError.SIMULATION_VERSION_CONFLICT,
                        versionConflictData(request.getVersion(), target.getVersion())
                );
            }

            FamilySnapshot lockedFamily = simulationMapper.lockFamily(target.getFamilyId());
            if (lockedFamily == null || !Objects.equals(lockedFamily.getUserId(), userId)) {
                throw new SimulationException(SimulationError.SIMULATION_ACCESS_DENIED);
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
            ProductSelectionPlan selectionPlan = validateProductSelections(
                    request,
                    target,
                    selectedPortfolio,
                    portfolioProducts
            );

            if (request.getClientCalculation() != null
                    && !FORMULA_VERSION.equals(
                    request.getClientCalculation().getFormulaVersion())) {
                throw new SimulationException(SimulationError.CALCULATION_VERSION_CONFLICT);
            }

            SimulationSaveResponse.PreviousSimulation previousSimulation = null;
            LocalDateTime now = LocalDateTime.now();
            if (replacingAnother) {
                restoreSimulationProducts(activeSaved);
                simulationMapper.deleteSimulationPreferentialConditions(
                        activeSaved.getSimulationId()
                );
                simulationMapper.clearSimulationSelections(activeSaved.getSimulationId());
                LocalDateTime previousExpiry = now.plusHours(DRAFT_RETENTION_HOURS);
                int reset = simulationMapper.resetSavedSimulation(
                        activeSaved.getSimulationId(),
                        previousExpiry,
                        now
                );
                if (reset != 1) {
                    throw new SimulationException(
                            SimulationError.PREVIOUS_SIMULATION_RESET_FAILED);
                }
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

            List<SimulationTrancheRecord> selectedTranches =
                    safeList(simulationMapper.selectTranches(simulationId)).stream()
                            .filter(item -> Objects.equals(
                                    item.getResultId(),
                                    selectedPortfolio.getResultId()
                            ))
                            .toList();
            SimulationResultRecord selectedResult =
                    safeList(simulationMapper.selectResults(simulationId)).stream()
                            .filter(item -> Objects.equals(
                                    item.getResultId(),
                                    selectedPortfolio.getResultId()
                            ))
                            .findFirst()
                            .orElseThrow(() -> new SimulationException(
                                    SimulationError.SIMULATION_RESULT_INCOMPLETE));

            long serverFutureValue = 0;
            List<SimulationProductRecord> selectedProducts = new ArrayList<>();
            for (SelectedProductPlan productPlan : selectionPlan.products()) {
                SimulationProductRecord product = productPlan.product();
                BigDecimal appliedRate = finalAppliedRate(
                        product,
                        target.getInvestmentPeriodMonths(),
                        productPlan.preferentialRates()
                );
                product.setAppliedAnnualRatePercent(appliedRate);
                long futureValue = calculator.calculateSelectedProductValue(
                        product,
                        product.getAllocatedAmount(),
                        selectedTranches,
                        selectedResult.getInvestmentPrincipal(),
                        target.getInvestmentEndDate()
                );
                product.setSelected(true);
                product.setExpectedFutureValue(futureValue);
                product.setSelectedPreferentialConditions(productPlan.preferentialRates());
                simulationMapper.markSimulationProductSelected(
                        product.getSimulationProductId(),
                        appliedRate,
                        futureValue
                );
                for (PreferentialRateRecord rate : productPlan.preferentialRates()) {
                    simulationMapper.insertSelectedPreferentialCondition(
                            product.getSimulationProductId(),
                            rate.getPreferentialInterestRateId()
                    );
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
                    target.getInvestmentPeriodMonths()
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
            idempotencyStore.remember(
                    userId,
                    "PUT:/api/gs/" + simulationId + "/save",
                    idempotencyKey,
                    fingerprint,
                    response
            );
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
        validateSnapshotCompleteness(results, tranches, portfolios, products, productDataVersion);

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
                                        simulation
                                ))
                                .toList()
                ))
                .toList();

        SimulationResponse.Selection selection = null;
        if (simulation.getStatus() == SimulationStatus.SAVED) {
            SimulationPortfolioRecord selectedPortfolio = portfolios.stream()
                    .filter(item -> Objects.equals(
                            item.getPortfolioId(),
                            simulation.getSelectedPortfolioId()
                    ))
                    .findFirst()
                    .orElseThrow(() -> new SimulationException(
                            SimulationError.SIMULATION_HISTORY_INCOMPLETE));
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
            if (selectedProducts.isEmpty()) {
                throw new SimulationException(
                        SimulationError.SIMULATION_HISTORY_INCOMPLETE);
            }
            selection = toSelection(
                    selectedPortfolio,
                    selectedResult,
                    selectedProducts,
                    simulation.getInvestmentPeriodMonths()
            );
        }

        return new SimulationResponse(
                simulationId,
                simulation.getStatus(),
                simulation.getVersion(),
                new SimulationResponse.Family(
                        simulation.getFamilyId(),
                        simulation.getFamilyName(),
                        simulation.getRelation(),
                        simulation.getBirthDate(),
                        simulation.getAgeAtSimulation(),
                        simulation.getMinorAtSimulation()
                ),
                new SimulationResponse.Input(
                        simulation.getRequestedAmount(),
                        simulation.getTaxPaymentMethod(),
                        simulation.getInvestmentPeriodMonths(),
                        simulation.getAsOfDate(),
                        simulation.getInvestmentEndDate()
                ),
                new SimulationResponse.GiftHistorySummary(
                        simulation.getLookbackStartDate(),
                        simulation.getPreviousGiftAmount(),
                        simulation.getDeductionLimit(),
                        simulation.getUsedDeductionAmount(),
                        simulation.getRemainingDeductionAmount(),
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

        Map<RiskProfile, SimulationPortfolioRecord> persisted =
                new EnumMap<>(RiskProfile.class);
        for (RiskProfile profile : RiskProfile.values()) {
            Map<ProductType, BigDecimal> ratios =
                    PortfolioPolicy.allocations(investmentPeriodMonths).get(profile);
            Map<ProductType, Long> amounts = allocationAmounts(
                    result.getInvestmentPrincipal(),
                    ratios,
                    aggregate.tranches(),
                    safeCandidates.get(ProductType.SAVINGS).get(0),
                    investmentEndDate
            );
            Map<ProductType, List<ProductCandidate>> candidates =
                    new EnumMap<>(ProductType.class);
            candidates.put(ProductType.DEPOSIT, safeCandidates.get(ProductType.DEPOSIT));
            candidates.put(ProductType.SAVINGS, safeCandidates.get(ProductType.SAVINGS));
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
                List<ProductCandidate> typeCandidates = candidates.get(type);
                if (allocatedAmount > 0 && (typeCandidates == null || typeCandidates.isEmpty())) {
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
                    first.getExpectedFutureValue(),
                    second.getExpectedFutureValue()
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

    private ScenarioAggregate immediateScenario(
            SimulationExecuteRequest request,
            long remainingDeduction,
            List<TaxBracket> brackets,
            LocalDate asOfDate
    ) {
        SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                request.getRequestedAmount(),
                remainingDeduction,
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
            long fullDeductionLimit,
            List<TaxBracket> brackets,
            LocalDate asOfDate,
            LocalDate renewalDate,
            List<GiftHistoryRecord> completedGifts,
            LocalDate investmentEndDate
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
        long totalInvestment = 0;
        int sequence = 1;

        long currentAmount = Math.min(amountLeft, remainingDeduction);
        if (currentAmount > 0) {
            SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                    currentAmount,
                    remainingDeduction,
                    request.getTaxPaymentMethod(),
                    brackets
            );
            tranches.add(tranche(sequence++, asOfDate, currentAmount, tax, true));
            history.add(new GiftPoint(asOfDate, currentAmount));
            amountLeft -= currentAmount;
            totalDeduction += tax.deductionAmount();
            totalTaxable += tax.taxableAmount();
            totalTax += tax.giftTax();
            totalDonorRequired += tax.donorRequiredAmount();
            totalInvestment += tax.investmentAmount();
        }

        LocalDate nextDate = renewalDate;
        int guard = 0;
        while (amountLeft > 0 && guard++ < 100) {
            LocalDate calculationDate = nextDate;
            long used = history.stream()
                    .filter(point -> point.date().isAfter(
                            calculationDate.minusYears(DEDUCTION_WINDOW_YEARS)))
                    .filter(point -> point.date().isBefore(calculationDate))
                    .mapToLong(GiftPoint::amount)
                    .sum();
            long available = Math.max(0, fullDeductionLimit - used);
            if (available == 0) {
                nextDate = nextReleaseDate(history, nextDate);
                continue;
            }
            long giftAmount = Math.min(amountLeft, available);
            SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                    giftAmount,
                    available,
                    request.getTaxPaymentMethod(),
                    brackets
            );
            boolean included = !nextDate.isAfter(investmentEndDate);
            tranches.add(tranche(sequence++, nextDate, giftAmount, tax, included));
            history.add(new GiftPoint(nextDate, giftAmount));
            amountLeft -= giftAmount;
            totalDeduction += tax.deductionAmount();
            totalTaxable += tax.taxableAmount();
            totalTax += tax.giftTax();
            totalDonorRequired += tax.donorRequiredAmount();
            if (included) {
                totalInvestment += tax.investmentAmount();
            }
            if (amountLeft > 0) {
                nextDate = nextReleaseDate(history, nextDate);
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
                Math.max(0, request.getRequestedAmount() - totalTax),
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
            ProductCandidate savingsCandidate,
            LocalDate investmentEndDate
    ) {
        Map<ProductType, Long> amounts = new EnumMap<>(ProductType.class);
        long deposit = ratioAmount(principal, ratios.get(ProductType.DEPOSIT));
        long savings = ratioAmount(principal, ratios.get(ProductType.SAVINGS));
        long etf = Math.max(0, principal - deposit - savings);
        if (savingsCandidate.getMonthlyMaxAmount() != null) {
            long capacity = tranches.stream()
                    .filter(item -> item.getInvestmentAmount() > 0)
                    .mapToLong(item -> safeMultiply(
                            savingsCandidate.getMonthlyMaxAmount(),
                            calculator.remainingMonths(
                                    item.getGiftDate(),
                                    investmentEndDate
                            )
                    ))
                    .sum();
            if (savings > capacity) {
                deposit += savings - capacity;
                savings = capacity;
            }
        }
        amounts.put(ProductType.DEPOSIT, deposit);
        amounts.put(ProductType.SAVINGS, savings);
        amounts.put(ProductType.ETF, etf);
        return amounts;
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
        long futureValue = investmentPrincipal <= 0 ? 0
                : calculator.calculateSelectedProductValue(
                product,
                allocatedAmount,
                tranches,
                investmentPrincipal,
                investmentEndDate
        );
        product.setExpectedFutureValue(futureValue);
        return product;
    }

    private ProductSelectionPlan validateProductSelections(
            SimulationSaveRequest request,
            SimulationRecord simulation,
            SimulationPortfolioRecord portfolio,
            List<SimulationProductRecord> candidates
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
            if (rates.size() != conditionCodes.size()
                    || rates.stream().anyMatch(rate -> !termMatches(
                    simulation.getInvestmentPeriodMonths(),
                    rate.getMinimumMonths(),
                    rate.getMaximumMonths()
            ))) {
                throw new SimulationException(
                        SimulationError.INVALID_PREFERENTIAL_CONDITION);
            }
            validateProductLimits(product, simulation.getInvestmentPeriodMonths());
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
            int investmentPeriodMonths
    ) {
        ProductVersionDetailRecord detail =
                simulationMapper.selectProductVersionDetail(product.getProductVersionId());
        if (detail == null) {
            throw new SimulationException(
                    SimulationError.PRODUCT_DATA_VERSION_MISMATCH);
        }
        if (!termMatches(
                investmentPeriodMonths,
                detail.getMinimumMonths(),
                detail.getMaximumMonths()
        )) {
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
            long monthly = divideCeiling(
                    product.getAllocatedAmount(),
                    investmentPeriodMonths
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

    private BigDecimal finalAppliedRate(
            SimulationProductRecord product,
            int months,
            List<PreferentialRateRecord> rates
    ) {
        if (product.getProductType() == ProductType.ETF) {
            return product.getBaseAnnualRatePercent();
        }
        BigDecimal additional = rates.stream()
                .map(PreferentialRateRecord::getAdditionalRatePercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal calculated = product.getBaseAnnualRatePercent().add(additional);
        BigDecimal maximum = product.getMaximumAnnualRatePercent();
        return maximum == null || calculated.compareTo(maximum) <= 0
                ? calculated : maximum;
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
        for (SimulationPortfolioRecord portfolio : portfolios) {
            SimulationResultRecord result = results.get(portfolio.getResultId());
            for (SimulationProductRecord product :
                    safeList(simulationMapper.selectPortfolioProducts(
                            portfolio.getPortfolioId()
                    ))) {
                BigDecimal baseRate = product.getBaseAnnualRatePercent();
                product.setAppliedAnnualRatePercent(baseRate);
                long value = calculator.calculateSelectedProductValue(
                        product,
                        product.getAllocatedAmount(),
                        tranches.getOrDefault(result.getResultId(), List.of()),
                        result.getInvestmentPrincipal(),
                        simulation.getInvestmentEndDate()
                );
                simulationMapper.restoreSimulationProduct(
                        product.getSimulationProductId(),
                        baseRate,
                        value
                );
            }
        }
    }

    private SimulationResponse.Portfolio toPortfolioResponse(
            SimulationPortfolioRecord portfolio,
            SimulationResultRecord result,
            List<SimulationProductRecord> products,
            SimulationRecord simulation
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
                                simulation.getInvestmentPeriodMonths()
                        ))
                        .toList()
        );
    }

    private SimulationResponse.Product toProductResponse(
            SimulationProductRecord product,
            long investmentPrincipal,
            int investmentPeriodMonths
    ) {
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
                Math.max(1, investmentPeriodMonths)
        ) : null;
        SimulationResponse.ReturnMetric metric =
                product.getProductType() == ProductType.ETF
                        ? new SimulationResponse.ReturnMetric(
                        "ANNUALIZED_RETURN_5Y",
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
                product.getExpectedFutureValue(),
                product.getExpectedFutureValue() - product.getAllocatedAmount(),
                product.isSelected(),
                conditions
        );
    }

    private SimulationResponse.Selection toSelection(
            SimulationPortfolioRecord portfolio,
            SimulationResultRecord result,
            List<SimulationProductRecord> selectedProducts,
            int investmentPeriodMonths
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
                futureValue,
                futureValue - result.getInvestmentPrincipal(),
                selectedProducts.stream()
                        .map(product -> toProductResponse(
                                product,
                                result.getInvestmentPrincipal(),
                                investmentPeriodMonths
                        ))
                        .toList()
        );
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
                "ANNUALIZED_RETURN_5Y",
                PortfolioPolicy.allocations(months)
        );
    }

    private void validateSnapshotCompleteness(
            List<SimulationResultRecord> results,
            List<SimulationTrancheRecord> tranches,
            List<SimulationPortfolioRecord> portfolios,
            List<SimulationProductRecord> products,
            ProductDataVersionRecord productDataVersion
    ) {
        if (productDataVersion == null || results.size() != 2 || tranches.isEmpty()
                || portfolios.size() != 6 || products.isEmpty()) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RESULT_INCOMPLETE);
        }
        for (SimulationResultRecord result : results) {
            long profileCount = portfolios.stream()
                    .filter(item -> Objects.equals(
                            item.getResultId(),
                            result.getResultId()
                    ))
                    .map(SimulationPortfolioRecord::getPortfolioType)
                    .distinct()
                    .count();
            if (profileCount != RiskProfile.values().length) {
                throw new SimulationException(
                        SimulationError.SIMULATION_RESULT_INCOMPLETE);
            }
        }
    }

    private Map<ProductType, List<ProductCandidate>> loadSafeAssetCandidates(
            Long dataVersionId,
            int months
    ) {
        Map<ProductType, List<ProductCandidate>> result =
                new EnumMap<>(ProductType.class);
        result.put(
                ProductType.DEPOSIT,
                requireCandidates(
                        simulationMapper.selectDepositCandidates(
                                dataVersionId,
                                months,
                                MAX_PRODUCT_CANDIDATES
                        )
                )
        );
        result.put(
                ProductType.SAVINGS,
                requireCandidates(
                        simulationMapper.selectSavingsCandidates(
                                dataVersionId,
                                months,
                                MAX_PRODUCT_CANDIDATES
                        )
                )
        );
        return result;
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
        return family;
    }

    SimulationRecord requireSimulation(Long simulationId, Long userId) {
        SimulationRecord simulation = simulationMapper.selectSimulation(simulationId);
        if (simulation == null) {
            throw new SimulationException(SimulationError.SIMULATION_NOT_FOUND);
        }
        if (!Objects.equals(simulation.getUserId(), userId)) {
            throw new SimulationException(SimulationError.SIMULATION_ACCESS_DENIED);
        }
        if (simulation.getStatus() == SimulationStatus.DRAFT
                && (simulation.getExpiredAt() == null
                || !simulation.getExpiredAt().isAfter(LocalDateTime.now()))) {
            throw new SimulationException(SimulationError.SIMULATION_EXPIRED);
        }
        if (simulation.getStatus() == SimulationStatus.SAVED
                && (simulation.getSelectedPortfolioId() == null
                || simulation.getSavedAt() == null)) {
            throw new SimulationException(
                    SimulationError.SIMULATION_HISTORY_INCOMPLETE);
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
                || request.getInvestmentPeriodMonths() == null) {
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
        saved.put(
                "expectedFutureValue",
                selected == null ? null : selected.getExpectedFutureValue()
        );
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

    private LocalDate resolveDeductionRenewalDate(
            List<GiftHistoryRecord> gifts,
            LocalDate asOfDate
    ) {
        return gifts.stream()
                .map(GiftHistoryRecord::getGiftDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .map(date -> date.plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1))
                .orElse(asOfDate);
    }

    private LocalDate nextReleaseDate(List<GiftPoint> history, LocalDate afterDate) {
        return history.stream()
                .map(point -> point.date().plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1))
                .filter(date -> date.isAfter(afterDate))
                .min(LocalDate::compareTo)
                .orElse(afterDate.plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1));
    }

    private boolean termMatches(int months, Integer minimum, Integer maximum) {
        return (minimum == null || months >= minimum)
                && (maximum == null || months <= maximum);
    }

    private String executeFingerprint(
            SimulationExecuteRequest request,
            LocalDate asOfDate
    ) {
        return request.getFamilyId() + "|"
                + request.getRequestedAmount() + "|"
                + request.getTaxPaymentMethod() + "|"
                + request.getInvestmentPeriodMonths() + "|"
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

    private long ratioAmount(long principal, BigDecimal ratio) {
        return BigDecimal.valueOf(principal)
                .multiply(ratio)
                .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
                .longValue();
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

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
