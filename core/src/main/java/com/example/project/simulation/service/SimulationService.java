package com.example.project.simulation.service;

import com.example.project.simulation.domain.DeductionRule;
import com.example.project.simulation.domain.FamilySnapshot;
import com.example.project.simulation.domain.GiftHistoryRecord;
import com.example.project.simulation.domain.ProductCandidate;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
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
import java.util.HashSet;
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
    public static final String CALCULATION_VERSION = "GIFT_SIM_V1";

    private static final int DEDUCTION_WINDOW_YEARS = 10;
    private static final int MAX_PRODUCT_CANDIDATES = 3;
    private static final int MAX_ETF_CANDIDATES = 9;
    private static final int MAX_INVESTMENT_MONTHS = 240;
    private static final int DRAFT_RETENTION_HOURS = 24;
    private static final int SAVED_RETENTION_DAYS = 90;
    private static final long CALCULATION_TOLERANCE_WON = 1L;

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
            LocalDate asOfDate = resolveAsOfDate(request.getAsOfDate());
            validateExecuteRequest(request);

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

            FamilySnapshot family = requireFamily(request.getFamilyId(), userId, false);
            boolean minor = Period.between(family.getBirthDate(), asOfDate).getYears() < 19;
            DeductionRule deductionRule = simulationMapper.selectDeductionRule(
                    family.getRelation(),
                    minor,
                    asOfDate
            );
            if (deductionRule == null || deductionRule.getDeductionLimit() == null) {
                throw new SimulationException(SimulationError.DEDUCTION_RULE_NOT_FOUND);
            }

            List<GiftHistoryRecord> completedGifts = simulationMapper.selectCompletedGifts(
                    family.getFamilyId(),
                    asOfDate.minusYears(DEDUCTION_WINDOW_YEARS),
                    asOfDate
            );
            if (completedGifts == null) {
                completedGifts = List.of();
            }

            long previousGiftAmount = completedGifts.stream()
                    .mapToLong(gift -> value(gift.getAmount()))
                    .sum();
            long deductionLimit = deductionRule.getDeductionLimit();
            long remainingDeduction = Math.max(0, deductionLimit - previousGiftAmount);
            LocalDate resetDate = resolveDeductionResetDate(
                    completedGifts.stream()
                            .map(GiftHistoryRecord::getGiftDate)
                            .min(LocalDate::compareTo)
                            .orElse(null),
                    asOfDate
            );

            List<TaxBracket> taxBrackets = simulationMapper.selectTaxBrackets(asOfDate);
            if (taxBrackets == null || taxBrackets.isEmpty()) {
                throw new SimulationException(SimulationError.TAX_BRACKET_NOT_FOUND);
            }

            Map<ProductType, List<ProductCandidate>> candidates = loadProductCandidates(
                    request.getInvestmentPeriodMonths()
            );
            candidates.values().stream()
                    .flatMap(List::stream)
                    .filter(candidate -> candidate.getProductDataDate() == null)
                    .forEach(candidate -> candidate.setProductDataDate(asOfDate));
            Map<ProductType, ProductCandidate> defaultProducts = candidates.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0)));
            LocalDate productDataDate = candidates.values().stream()
                    .flatMap(List::stream)
                    .map(ProductCandidate::getProductDataDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(asOfDate);

            LocalDate evaluationDate = asOfDate.plusMonths(request.getInvestmentPeriodMonths());
            ScenarioAggregate immediate = immediateScenario(
                    request,
                    remainingDeduction,
                    taxBrackets,
                    asOfDate,
                    evaluationDate
            );
            ScenarioAggregate optimized = optimizedScenario(
                    request,
                    remainingDeduction,
                    deductionLimit,
                    taxBrackets,
                    asOfDate,
                    resetDate,
                    evaluationDate,
                    completedGifts
            );

            Map<ProductType, BigDecimal> defaultAllocation =
                    PortfolioPolicy.allocations(request.getInvestmentPeriodMonths())
                            .get(RiskProfile.BALANCED);
            setDefaultFutureValue(immediate, evaluationDate, defaultAllocation, defaultProducts);
            setDefaultFutureValue(optimized, evaluationDate, defaultAllocation, defaultProducts);

            ScenarioType recommendedScenario = recommend(immediate.result(), optimized.result());
            LocalDateTime createdAt = LocalDateTime.now();

            SimulationRecord simulation = new SimulationRecord();
            simulation.setFamilyId(family.getFamilyId());
            simulation.setRequestedAmount(request.getRequestedAmount());
            simulation.setStatus(SimulationStatus.DRAFT);
            simulation.setTaxPaymentMethod(request.getTaxPaymentMethod());
            simulation.setInvestmentPeriodMonths(request.getInvestmentPeriodMonths());
            simulation.setAsOfDate(asOfDate);
            simulation.setInvestmentEndDate(evaluationDate);
            simulation.setRecommendedScenarioType(recommendedScenario);
            simulation.setPreviousGiftAmount(previousGiftAmount);
            simulation.setRemainingDeductionAmount(remainingDeduction);
            simulation.setDeductionResetDate(resetDate);
            simulation.setCalculationVersion(CALCULATION_VERSION);
            simulation.setFormulaVersion(FORMULA_VERSION);
            simulation.setProductDataDate(productDataDate);
            simulation.setVersion(1L);
            simulation.setCreatedAt(createdAt);
            simulation.setExpiredAt(createdAt.plusHours(DRAFT_RETENTION_HOURS));
            simulationMapper.insertSimulation(simulation);

            persistScenario(
                    simulation.getSimulationId(),
                    immediate,
                    candidates,
                    evaluationDate
            );
            persistScenario(
                    simulation.getSimulationId(),
                    optimized,
                    candidates,
                    evaluationDate
            );

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
            if (simulationId == null || simulationId <= 0) {
                throw new SimulationException(SimulationError.INVALID_SIMULATION_ID);
            }

            SimulationRecord simulation = requireSimulation(simulationId, userId);
            List<SimulationResultRecord> results = simulationMapper.selectResults(simulationId);
            List<SimulationTrancheRecord> tranches = simulationMapper.selectTranches(simulationId);
            List<SimulationProductRecord> products = simulationMapper.selectProductSnapshots(simulationId);

            if (results == null || results.size() != 2 || tranches == null || tranches.isEmpty()
                    || products == null || products.isEmpty()) {
                throw new SimulationException(SimulationError.SIMULATION_RESULT_INCOMPLETE);
            }

            return toResponse(simulation, results, tranches, products);
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

            SimulationRecord simulation = requireSimulation(simulationId, userId);
            if (!Objects.equals(simulation.getVersion(), request.getVersion())) {
                throw new SimulationException(SimulationError.SIMULATION_VERSION_CONFLICT);
            }

            List<SimulationResultRecord> results = simulationMapper.selectResults(simulationId);
            SimulationResultRecord selectedResult = results.stream()
                    .filter(result -> result.getResultId().equals(request.getResultId()))
                    .filter(result -> result.getScenarioType() == request.getSelectedScenarioType())
                    .findFirst()
                    .orElseThrow(() -> new SimulationException(
                            SimulationError.SIMULATION_RESULT_NOT_FOUND));

            List<SimulationTrancheRecord> selectedTranches = simulationMapper.selectTranches(simulationId)
                    .stream()
                    .filter(tranche -> tranche.getResultId().equals(selectedResult.getResultId()))
                    .toList();
            if (selectedTranches.isEmpty()) {
                throw new SimulationException(SimulationError.SIMULATION_RESULT_INCOMPLETE);
            }

            List<SimulationProductRecord> allSnapshots =
                    simulationMapper.selectProductSnapshots(simulationId);
            Map<Long, SimulationProductRecord> candidateByProductId = allSnapshots.stream()
                    .filter(product -> product.getResultId().equals(selectedResult.getResultId()))
                    .collect(Collectors.toMap(
                            SimulationProductRecord::getProductId,
                            Function.identity(),
                            (first, ignored) -> first
                    ));

            validateUniqueProducts(request);
            long allocationSum = request.getProducts().stream()
                    .mapToLong(SimulationSaveRequest.SelectedProduct::getAllocatedAmount)
                    .sum();
            if (allocationSum != selectedResult.getInvestmentPrincipal()) {
                throw new SimulationException(SimulationError.ALLOCATION_SUM_MISMATCH);
            }

            Map<ProductType, Long> allocationByType = new EnumMap<>(ProductType.class);
            for (SimulationSaveRequest.SelectedProduct selected : request.getProducts()) {
                SimulationProductRecord snapshot = candidateByProductId.get(selected.getProductId());
                if (snapshot == null) {
                    throw new SimulationException(SimulationError.PRODUCT_SNAPSHOT_MISMATCH);
                }
                if (snapshot.getProductType() != selected.getRecommendationType()) {
                    throw new SimulationException(SimulationError.PRODUCT_TYPE_MISMATCH);
                }
                validateProductLimit(snapshot, selected.getAllocatedAmount(),
                        simulation.getInvestmentPeriodMonths());
                allocationByType.merge(
                        selected.getRecommendationType(),
                        selected.getAllocatedAmount(),
                        Long::sum
                );
            }

            RiskProfile riskProfile = PortfolioPolicy.resolveProfile(
                    allocationByType,
                    selectedResult.getInvestmentPrincipal(),
                    simulation.getInvestmentPeriodMonths(),
                    request.getRiskProfile()
            );
            validateEtfPolicy(request, candidateByProductId, riskProfile);

            if (request.getClientCalculation() != null
                    && !FORMULA_VERSION.equals(request.getClientCalculation().getFormulaVersion())) {
                throw new SimulationException(SimulationError.CALCULATION_VERSION_CONFLICT);
            }

            results.forEach(result -> simulationMapper.clearSelectedProducts(result.getResultId()));
            List<SimulationProductRecord> selectedProducts = new ArrayList<>();
            long serverFutureValue = 0;

            for (SimulationSaveRequest.SelectedProduct selected : request.getProducts()) {
                SimulationProductRecord snapshot = candidateByProductId.get(selected.getProductId());
                long expectedFutureValue = calculator.calculateSelectedProductValue(
                        snapshot,
                        selected.getAllocatedAmount(),
                        selectedTranches,
                        selectedResult.getInvestmentPrincipal(),
                        simulation.getInvestmentEndDate()
                );
                long expectedProfit = expectedFutureValue - selected.getAllocatedAmount();
                BigDecimal allocationRatio = BigDecimal.valueOf(selected.getAllocatedAmount())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(
                                BigDecimal.valueOf(selectedResult.getInvestmentPrincipal()),
                                2,
                                RoundingMode.HALF_UP
                        );

                snapshot.setSelected(true);
                snapshot.setAllocatedAmount(selected.getAllocatedAmount());
                snapshot.setAllocationRatio(allocationRatio);
                snapshot.setExpectedFutureValue(expectedFutureValue);
                snapshot.setExpectedProfit(expectedProfit);

                simulationMapper.selectProduct(
                        snapshot.getSimulationProductId(),
                        snapshot.getAllocatedAmount(),
                        allocationRatio,
                        snapshot.getAppliedAnnualRatePercent(),
                        expectedFutureValue,
                        expectedProfit
                );
                selectedProducts.add(snapshot);
                serverFutureValue += expectedFutureValue;
            }

            simulationMapper.updateResultFutureValue(
                    selectedResult.getResultId(),
                    serverFutureValue,
                    riskProfile
            );
            LocalDateTime savedAt = LocalDateTime.now();
            LocalDateTime expiresAt = savedAt.plusDays(SAVED_RETENTION_DAYS);
            int updated = simulationMapper.saveSimulation(
                    simulationId,
                    request.getVersion(),
                    request.getSelectedScenarioType(),
                    request.getResultId(),
                    riskProfile,
                    savedAt,
                    expiresAt
            );
            if (updated != 1) {
                throw new SimulationException(SimulationError.SIMULATION_VERSION_CONFLICT);
            }

            long serverProfit = serverFutureValue - selectedResult.getInvestmentPrincipal();
            Long clientFutureValue = request.getClientCalculation() == null
                    ? null : request.getClientCalculation().getExpectedFutureValue();
            Long clientProfit = request.getClientCalculation() == null
                    ? null : request.getClientCalculation().getExpectedProfit();
            long futureDifference = clientFutureValue == null ? 0 : serverFutureValue - clientFutureValue;
            long profitDifference = clientProfit == null ? 0 : serverProfit - clientProfit;

            SimulationSaveResponse response = new SimulationSaveResponse(
                    simulationId,
                    SimulationStatus.SAVED,
                    request.getVersion() + 1,
                    request.getSelectedScenarioType(),
                    riskProfile,
                    selectedResult.getResultId(),
                    selectedResult.getGiftTax(),
                    selectedResult.getInvestmentPrincipal(),
                    selectedProducts.stream().map(this::toProductResponse).toList(),
                    new SimulationSaveResponse.ServerCalculation(
                            FORMULA_VERSION,
                            serverFutureValue,
                            serverProfit
                    ),
                    Math.abs(futureDifference) > CALCULATION_TOLERANCE_WON
                            || Math.abs(profitDifference) > CALCULATION_TOLERANCE_WON,
                    new SimulationSaveResponse.ClientServerDifference(
                            futureDifference,
                            profitDifference
                    ),
                    savedAt,
                    expiresAt
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

    private ScenarioAggregate immediateScenario(
            SimulationExecuteRequest request,
            long remainingDeduction,
            List<TaxBracket> brackets,
            LocalDate asOfDate,
            LocalDate evaluationDate
    ) {
        SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                request.getRequestedAmount(),
                remainingDeduction,
                request.getTaxPaymentMethod(),
                brackets
        );
        SimulationResultRecord result = new SimulationResultRecord();
        result.setScenarioType(ScenarioType.IMMEDIATE);
        result.setRiskProfile(RiskProfile.BALANCED);
        result.setDeductionAmount(tax.deductionAmount());
        result.setTaxableAmount(tax.taxableAmount());
        result.setGiftTax(tax.giftTax());
        result.setDonorRequiredAmount(tax.donorRequiredAmount());
        result.setPostTaxAmount(tax.investmentAmount());
        result.setCurrentAmount(request.getRequestedAmount());
        result.setDeferredAmount(0L);
        result.setInvestmentPrincipal(tax.investmentAmount());
        result.setCreatedAt(LocalDateTime.now());

        SimulationTrancheRecord tranche = tranche(
                1,
                asOfDate,
                request.getRequestedAmount(),
                tax,
                !asOfDate.isAfter(evaluationDate)
        );
        return new ScenarioAggregate(result, new ArrayList<>(List.of(tranche)), new EnumMap<>(ProductType.class));
    }

    private ScenarioAggregate optimizedScenario(
            SimulationExecuteRequest request,
            long remainingDeduction,
            long fullDeductionLimit,
            List<TaxBracket> brackets,
            LocalDate asOfDate,
            LocalDate resetDate,
            LocalDate evaluationDate,
            List<GiftHistoryRecord> completedGifts
    ) {
        long remainingAmount = request.getRequestedAmount();
        long currentAmount = Math.min(remainingAmount, remainingDeduction);
        List<SimulationTrancheRecord> tranches = new ArrayList<>();
        long totalTax = 0;
        long totalDonorRequired = 0;
        long totalInvestmentPrincipal = 0;
        long totalDeduction = 0;
        int sequence = 1;

        if (currentAmount > 0) {
            SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                    currentAmount,
                    remainingDeduction,
                    request.getTaxPaymentMethod(),
                    brackets
            );
            tranches.add(tranche(sequence++, asOfDate, currentAmount, tax, true));
            remainingAmount -= currentAmount;
            totalTax += tax.giftTax();
            totalDonorRequired += tax.donorRequiredAmount();
            totalInvestmentPrincipal += tax.investmentAmount();
            totalDeduction += tax.deductionAmount();
        }

        List<GiftPoint> deductionHistory = completedGifts.stream()
                .map(gift -> new GiftPoint(gift.getGiftDate(), value(gift.getAmount())))
                .collect(Collectors.toCollection(ArrayList::new));
        if (currentAmount > 0) {
            deductionHistory.add(new GiftPoint(asOfDate, currentAmount));
        }

        LocalDate giftDate = resetDate;
        int safety = 0;
        while (remainingAmount > 0 && safety++ < 100) {
            LocalDate calculationDate = giftDate;
            long usedDeduction = deductionHistory.stream()
                    .filter(point -> point.date().isAfter(
                            calculationDate.minusYears(DEDUCTION_WINDOW_YEARS)))
                    .filter(point -> point.date().isBefore(calculationDate))
                    .mapToLong(GiftPoint::amount)
                    .sum();
            long availableDeduction = Math.max(0, fullDeductionLimit - usedDeduction);

            if (availableDeduction == 0) {
                giftDate = nextReleaseDate(deductionHistory, giftDate);
                continue;
            }

            long trancheAmount = Math.min(remainingAmount, availableDeduction);
            SimulationCalculator.TaxOutcome tax = calculator.calculateTax(
                    trancheAmount,
                    availableDeduction,
                    request.getTaxPaymentMethod(),
                    brackets
            );
            boolean withinEvaluation = !giftDate.isAfter(evaluationDate);
            SimulationTrancheRecord tranche = tranche(
                    sequence++,
                    giftDate,
                    trancheAmount,
                    tax,
                    withinEvaluation
            );
            tranches.add(tranche);
            deductionHistory.add(new GiftPoint(giftDate, trancheAmount));
            remainingAmount -= trancheAmount;
            totalTax += tax.giftTax();
            totalDonorRequired += tax.donorRequiredAmount();
            totalDeduction += tax.deductionAmount();
            if (withinEvaluation) {
                totalInvestmentPrincipal += tax.investmentAmount();
            }
            if (remainingAmount > 0) {
                giftDate = nextReleaseDate(deductionHistory, giftDate);
            }
        }

        SimulationResultRecord result = new SimulationResultRecord();
        result.setScenarioType(ScenarioType.TAX_OPTIMIZED);
        result.setRiskProfile(RiskProfile.BALANCED);
        result.setDeductionAmount(totalDeduction);
        result.setTaxableAmount(0L);
        result.setGiftTax(totalTax);
        result.setDonorRequiredAmount(totalDonorRequired);
        result.setPostTaxAmount(request.getRequestedAmount() - totalTax);
        result.setCurrentAmount(currentAmount);
        result.setDeferredAmount(request.getRequestedAmount() - currentAmount);
        result.setInvestmentPrincipal(totalInvestmentPrincipal);
        result.setCreatedAt(LocalDateTime.now());
        return new ScenarioAggregate(result, tranches, new EnumMap<>(ProductType.class));
    }

    private SimulationTrancheRecord tranche(
            int sequence,
            LocalDate giftDate,
            long giftAmount,
            SimulationCalculator.TaxOutcome tax,
            boolean includedInEvaluation
    ) {
        SimulationTrancheRecord tranche = new SimulationTrancheRecord();
        tranche.setSequenceNo(sequence);
        tranche.setGiftDate(giftDate);
        tranche.setGiftAmount(giftAmount);
        tranche.setEstimatedGiftTax(tax.giftTax());
        tranche.setDonorRequiredAmount(tax.donorRequiredAmount());
        tranche.setInvestmentAmount(includedInEvaluation ? tax.investmentAmount() : 0L);
        return tranche;
    }

    private void setDefaultFutureValue(
            ScenarioAggregate aggregate,
            LocalDate evaluationDate,
            Map<ProductType, BigDecimal> defaultAllocation,
            Map<ProductType, ProductCandidate> defaultProducts
    ) {
        if (aggregate.result().getInvestmentPrincipal() <= 0) {
            aggregate.result().setExpectedFutureValue(0L);
            return;
        }

        long principal = aggregate.result().getInvestmentPrincipal();
        long depositAmount = ratioAmount(principal, defaultAllocation.get(ProductType.DEPOSIT));
        long savingsAmount = ratioAmount(principal, defaultAllocation.get(ProductType.SAVINGS));
        long etfAmount = principal - depositAmount - savingsAmount;

        ProductCandidate savings = defaultProducts.get(ProductType.SAVINGS);
        if (savings.getMonthlyMaxAmount() != null) {
            long savingsCapacity = aggregate.tranches().stream()
                    .filter(tranche -> tranche.getInvestmentAmount() > 0)
                    .mapToLong(tranche -> {
                        int remainingMonths = calculator.remainingMonths(
                                tranche.getGiftDate(),
                                evaluationDate
                        );
                        return safeMultiply(savings.getMonthlyMaxAmount(), remainingMonths);
                    })
                    .sum();
            if (savingsAmount > savingsCapacity) {
                depositAmount += savingsAmount - savingsCapacity;
                savingsAmount = savingsCapacity;
            }
        }

        aggregate.defaultAllocationAmounts().put(ProductType.DEPOSIT, depositAmount);
        aggregate.defaultAllocationAmounts().put(ProductType.SAVINGS, savingsAmount);
        aggregate.defaultAllocationAmounts().put(ProductType.ETF, etfAmount);

        long expectedFutureValue = 0;
        for (ProductType type : ProductType.values()) {
            ProductCandidate candidate = defaultProducts.get(type);
            SimulationProductRecord product = new SimulationProductRecord();
            product.setCalculationType(candidate.calculationType());
            product.setAppliedAnnualRatePercent(candidate.getAppliedAnnualRatePercent());
            expectedFutureValue += calculator.calculateSelectedProductValue(
                    product,
                    aggregate.defaultAllocationAmounts().get(type),
                    aggregate.tranches(),
                    principal,
                    evaluationDate
            );
        }
        aggregate.result().setExpectedFutureValue(expectedFutureValue);
    }

    private void persistScenario(
            Long simulationId,
            ScenarioAggregate aggregate,
            Map<ProductType, List<ProductCandidate>> candidates,
            LocalDate evaluationDate
    ) {
        SimulationResultRecord result = aggregate.result();
        result.setSimulationId(simulationId);
        simulationMapper.insertResult(result);

        for (SimulationTrancheRecord tranche : aggregate.tranches()) {
            tranche.setResultId(result.getResultId());
            simulationMapper.insertTranche(tranche);
        }

        for (Map.Entry<ProductType, List<ProductCandidate>> entry : candidates.entrySet()) {
            ProductType type = entry.getKey();
            long categoryAllocation = aggregate.defaultAllocationAmounts()
                    .getOrDefault(type, 0L);
            BigDecimal categoryRatio = result.getInvestmentPrincipal() <= 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(categoryAllocation)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            BigDecimal.valueOf(result.getInvestmentPrincipal()),
                            4,
                            RoundingMode.HALF_UP
                    );

            for (ProductCandidate candidate : entry.getValue()) {
                SimulationProductRecord snapshot = snapshot(
                        result.getResultId(),
                        candidate,
                        categoryAllocation,
                        categoryRatio,
                        aggregate.tranches(),
                        result.getInvestmentPrincipal(),
                        evaluationDate
                );
                simulationMapper.insertProductSnapshot(snapshot);
            }
        }
    }

    private SimulationProductRecord snapshot(
            Long resultId,
            ProductCandidate candidate,
            long allocatedAmount,
            BigDecimal allocationRatio,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate evaluationDate
    ) {
        SimulationProductRecord snapshot = new SimulationProductRecord();
        snapshot.setResultId(resultId);
        snapshot.setProductId(candidate.getProductId());
        snapshot.setProductName(candidate.getProductName());
        snapshot.setProductType(candidate.getProductType());
        snapshot.setProductCategory(candidate.getProductCategory());
        snapshot.setSelected(false);
        snapshot.setAllocatedAmount(allocatedAmount);
        snapshot.setAllocationRatio(allocationRatio);
        snapshot.setMinAnnualRatePercent(candidate.getMinAnnualRatePercent());
        snapshot.setMaxAnnualRatePercent(candidate.getMaxAnnualRatePercent());
        snapshot.setAppliedAnnualRatePercent(candidate.getAppliedAnnualRatePercent());
        snapshot.setCalculationType(candidate.calculationType());
        snapshot.setMinMonth(candidate.getMinMonth());
        snapshot.setMaxMonth(candidate.getMaxMonth());
        snapshot.setMinAmount(candidate.getMinAmount());
        snapshot.setMaxAmount(candidate.getMaxAmount());
        snapshot.setMonthlyMinAmount(candidate.getMonthlyMinAmount());
        snapshot.setMonthlyMaxAmount(candidate.getMonthlyMaxAmount());
        snapshot.setPreferentialConditions(candidate.getPreferentialConditions());
        snapshot.setTrackingIndex(candidate.getTrackingIndex());
        snapshot.setMarketCapitalization(candidate.getMarketCapitalization());
        snapshot.setDividendYieldPercent(candidate.getDividendYieldPercent());
        snapshot.setRiskLevel(candidate.getRiskLevel());
        snapshot.setProductDetailUrl(candidate.getProductDetailUrl());
        snapshot.setProductDataDate(candidate.getProductDataDate());

        if (investmentPrincipal > 0) {
            long futureValue = calculator.calculateSelectedProductValue(
                    snapshot,
                    allocatedAmount,
                    tranches,
                    investmentPrincipal,
                    evaluationDate
            );
            snapshot.setExpectedFutureValue(futureValue);
            snapshot.setExpectedProfit(futureValue - allocatedAmount);
        } else {
            snapshot.setExpectedFutureValue(0L);
            snapshot.setExpectedProfit(0L);
        }
        return snapshot;
    }

    private SimulationResponse toResponse(
            SimulationRecord simulation,
            List<SimulationResultRecord> results,
            List<SimulationTrancheRecord> tranches,
            List<SimulationProductRecord> products
    ) {
        Map<Long, List<SimulationTrancheRecord>> tranchesByResult = tranches.stream()
                .collect(Collectors.groupingBy(SimulationTrancheRecord::getResultId));
        Map<Long, List<SimulationProductRecord>> productsByResult = products.stream()
                .collect(Collectors.groupingBy(SimulationProductRecord::getResultId));

        List<SimulationResponse.Result> resultResponses = results.stream()
                .map(result -> new SimulationResponse.Result(
                        result.getResultId(),
                        result.getScenarioType(),
                        result.getDeductionAmount(),
                        result.getTaxableAmount(),
                        result.getGiftTax(),
                        result.getDonorRequiredAmount(),
                        result.getPostTaxAmount(),
                        result.getCurrentAmount(),
                        result.getDeferredAmount(),
                        result.getInvestmentPrincipal(),
                        result.getExpectedFutureValue(),
                        tranchesByResult.getOrDefault(result.getResultId(), List.of())
                                .stream()
                                .map(this::toTrancheResponse)
                                .toList(),
                        productsByResult.getOrDefault(result.getResultId(), List.of())
                                .stream()
                                .map(this::toProductResponse)
                                .toList()
                ))
                .toList();

        SimulationResponse.SelectedResult selectedResult = null;
        if (simulation.getStatus() == SimulationStatus.SAVED) {
            SimulationResultRecord selected = results.stream()
                    .filter(result -> result.getResultId().equals(simulation.getSelectedResultId()))
                    .findFirst()
                    .orElseThrow(() -> new SimulationException(
                            SimulationError.SIMULATION_RESULT_INCOMPLETE));
            List<SimulationProductRecord> selectedProducts = productsByResult
                    .getOrDefault(selected.getResultId(), List.of())
                    .stream()
                    .filter(SimulationProductRecord::isSelected)
                    .toList();
            if (selectedProducts.isEmpty()) {
                throw new SimulationException(SimulationError.SIMULATION_RESULT_INCOMPLETE);
            }
            long futureValue = selectedProducts.stream()
                    .mapToLong(product -> value(product.getExpectedFutureValue()))
                    .sum();
            selectedResult = new SimulationResponse.SelectedResult(
                    selected.getResultId(),
                    selected.getScenarioType(),
                    simulation.getSelectedRiskProfile(),
                    selected.getGiftTax(),
                    selected.getInvestmentPrincipal(),
                    futureValue,
                    futureValue - selected.getInvestmentPrincipal(),
                    selectedProducts.stream().map(this::toProductResponse).toList()
            );
        }

        return new SimulationResponse(
                simulation.getSimulationId(),
                simulation.getStatus(),
                simulation.getVersion(),
                new SimulationResponse.Family(
                        simulation.getFamilyId(),
                        simulation.getFamilyName(),
                        simulation.getRelation()
                ),
                new SimulationResponse.Input(
                        simulation.getRequestedAmount(),
                        simulation.getTaxPaymentMethod(),
                        simulation.getInvestmentPeriodMonths(),
                        simulation.getAsOfDate(),
                        simulation.getInvestmentEndDate()
                ),
                simulation.getPreviousGiftAmount(),
                simulation.getRemainingDeductionAmount(),
                simulation.getDeductionResetDate(),
                simulation.getRecommendedScenarioType(),
                simulation.getSelectedScenarioType(),
                selectedResult,
                resultResponses,
                frontendPolicy(simulation.getInvestmentPeriodMonths()),
                simulation.getFormulaVersion(),
                simulation.getCalculationVersion(),
                simulation.getProductDataDate(),
                simulation.getCreatedAt(),
                simulation.getSavedAt(),
                simulation.getExpiredAt()
        );
    }

    private SimulationResponse.FrontendCalculationPolicy frontendPolicy(int months) {
        return new SimulationResponse.FrontendCalculationPolicy(
                FORMULA_VERSION,
                "P × (1 + r × months / 12)",
                "월말 납입 적립식 미래가치",
                "P × (1 + recent5YearAnnualReturn)^(months / 12)",
                "END_OF_MONTH",
                "RECENT_5_YEAR_ANNUALIZED_RETURN",
                PortfolioPolicy.allocations(months)
        );
    }

    private SimulationResponse.Tranche toTrancheResponse(SimulationTrancheRecord tranche) {
        return new SimulationResponse.Tranche(
                tranche.getSequenceNo(),
                tranche.getGiftDate(),
                tranche.getGiftAmount(),
                tranche.getEstimatedGiftTax(),
                tranche.getDonorRequiredAmount(),
                tranche.getInvestmentAmount()
        );
    }

    private SimulationResponse.Product toProductResponse(SimulationProductRecord product) {
        Long allocatedAmount = product.isSelected()
                ? product.getAllocatedAmount()
                : product.getDefaultAllocatedAmount();
        BigDecimal allocationRatio = product.isSelected()
                ? product.getAllocationRatio()
                : product.getDefaultAllocationRatio();
        Long expectedFutureValue = product.isSelected()
                ? product.getExpectedFutureValue()
                : product.getDefaultExpectedFutureValue();
        Long expectedProfit = product.isSelected()
                ? product.getExpectedProfit()
                : product.getDefaultExpectedProfit();

        return new SimulationResponse.Product(
                product.getSimulationProductId(),
                product.getProductId(),
                product.getProductName(),
                product.getProductType(),
                product.getProductType(),
                product.getProductCategory(),
                product.getMinAnnualRatePercent(),
                product.getMaxAnnualRatePercent(),
                product.getAppliedAnnualRatePercent(),
                product.getCalculationType(),
                allocatedAmount,
                allocationRatio,
                expectedFutureValue,
                expectedProfit,
                product.getMinMonth(),
                product.getMaxMonth(),
                product.getMinAmount(),
                product.getMaxAmount(),
                product.getMonthlyMinAmount(),
                product.getMonthlyMaxAmount(),
                product.getPreferentialConditions(),
                product.getTrackingIndex(),
                product.getMarketCapitalization(),
                product.getDividendYieldPercent(),
                product.getRiskLevel(),
                product.getProductDetailUrl(),
                product.getProductDataDate()
        );
    }

    private Map<ProductType, List<ProductCandidate>> loadProductCandidates(int months) {
        Map<ProductType, List<ProductCandidate>> candidates = new EnumMap<>(ProductType.class);
        candidates.put(
                ProductType.DEPOSIT,
                validCandidates(
                        simulationMapper.selectDepositCandidates(months, MAX_PRODUCT_CANDIDATES),
                        ProductType.DEPOSIT
                )
        );
        candidates.put(
                ProductType.SAVINGS,
                validCandidates(
                        simulationMapper.selectSavingsCandidates(months, MAX_PRODUCT_CANDIDATES),
                        ProductType.SAVINGS
                )
        );
        candidates.put(
                ProductType.ETF,
                validCandidates(
                        simulationMapper.selectEtfCandidates(MAX_ETF_CANDIDATES),
                        ProductType.ETF
                )
        );
        if (candidates.values().stream().anyMatch(list -> list == null || list.isEmpty())) {
            throw new SimulationException(SimulationError.PRODUCT_DATA_NOT_READY);
        }
        return candidates;
    }

    private List<ProductCandidate> validCandidates(
            List<ProductCandidate> candidates,
            ProductType expectedType
    ) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> candidate.getProductId() != null)
                .filter(candidate -> candidate.getProductName() != null)
                .filter(candidate -> candidate.getAppliedAnnualRatePercent() != null)
                .peek(candidate -> {
                    if (candidate.getProductType() == null) {
                        candidate.setProductType(expectedType);
                    }
                })
                .filter(candidate -> candidate.getProductType() == expectedType)
                .toList();
    }

    private ScenarioType recommend(
            SimulationResultRecord immediate,
            SimulationResultRecord optimized
    ) {
        int futureValueComparison = Long.compare(
                immediate.getExpectedFutureValue(),
                optimized.getExpectedFutureValue()
        );
        if (futureValueComparison > 0) {
            return ScenarioType.IMMEDIATE;
        }
        if (futureValueComparison < 0) {
            return ScenarioType.TAX_OPTIMIZED;
        }
        return immediate.getGiftTax() <= optimized.getGiftTax()
                ? ScenarioType.IMMEDIATE
                : ScenarioType.TAX_OPTIMIZED;
    }

    private FamilySnapshot requireFamily(Long familyId, Long userId, boolean simulationContext) {
        FamilySnapshot family = simulationMapper.selectFamily(familyId);
        if (family == null) {
            throw new SimulationException(SimulationError.FAMILY_NOT_FOUND);
        }
        if (!family.getUserId().equals(userId)) {
            throw new SimulationException(
                    simulationContext
                            ? SimulationError.SIMULATION_ACCESS_DENIED
                            : SimulationError.FAMILY_ACCESS_DENIED
            );
        }
        return family;
    }

    private SimulationRecord requireSimulation(Long simulationId, Long userId) {
        SimulationRecord simulation = simulationMapper.selectSimulation(simulationId);
        if (simulation == null) {
            throw new SimulationException(SimulationError.SIMULATION_NOT_FOUND);
        }
        if (!simulation.getUserId().equals(userId)) {
            throw new SimulationException(SimulationError.SIMULATION_ACCESS_DENIED);
        }
        if (simulation.getExpiredAt() == null || !simulation.getExpiredAt().isAfter(LocalDateTime.now())) {
            throw new SimulationException(SimulationError.SIMULATION_EXPIRED);
        }
        return simulation;
    }

    private void validateUser(Long userId) {
        if (userId == null || userMapper.findById(userId) == null) {
            throw new SimulationException(SimulationError.USER_NOT_FOUND);
        }
    }

    private void validateExecuteRequest(SimulationExecuteRequest request) {
        if (request == null
                || request.getFamilyId() == null
                || request.getFamilyId() <= 0
                || request.getRequestedAmount() == null
                || request.getRequestedAmount() <= 0
                || request.getTaxPaymentMethod() == null
                || request.getInvestmentPeriodMonths() == null
                || request.getInvestmentPeriodMonths() < 1
                || request.getInvestmentPeriodMonths() > MAX_INVESTMENT_MONTHS) {
            throw new SimulationException(SimulationError.INVALID_REQUEST);
        }
    }

    private void validateSaveRequest(Long simulationId, SimulationSaveRequest request) {
        if (simulationId == null || simulationId <= 0) {
            throw new SimulationException(SimulationError.INVALID_SIMULATION_ID);
        }
        if (request == null
                || request.getVersion() == null
                || request.getVersion() <= 0
                || request.getSelectedScenarioType() == null
                || request.getResultId() == null
                || request.getResultId() <= 0
                || request.getProducts() == null
                || request.getProducts().isEmpty()) {
            throw new SimulationException(SimulationError.INVALID_SAVE_REQUEST);
        }
    }

    private void validateUniqueProducts(SimulationSaveRequest request) {
        Set<Long> ids = new HashSet<>();
        boolean invalid = request.getProducts().stream().anyMatch(product ->
                product.getProductId() == null
                        || product.getProductId() <= 0
                        || product.getRecommendationType() == null
                        || product.getAllocatedAmount() == null
                        || product.getAllocatedAmount() <= 0
                        || !ids.add(product.getProductId()));
        if (invalid) {
            throw new SimulationException(SimulationError.INVALID_SAVE_REQUEST);
        }
    }

    private void validateProductLimit(
            SimulationProductRecord product,
            long allocatedAmount,
            int investmentPeriodMonths
    ) {
        if (product.getProductType() == ProductType.DEPOSIT) {
            if ((product.getMinAmount() != null && allocatedAmount < product.getMinAmount())
                    || (product.getMaxAmount() != null && allocatedAmount > product.getMaxAmount())) {
                throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
            }
        }
        if (product.getProductType() == ProductType.SAVINGS) {
            long monthlyAmount = BigDecimal.valueOf(allocatedAmount)
                    .divide(
                            BigDecimal.valueOf(investmentPeriodMonths),
                            0,
                            RoundingMode.CEILING
                    )
                    .longValue();
            if ((product.getMonthlyMinAmount() != null
                    && monthlyAmount < product.getMonthlyMinAmount())
                    || (product.getMonthlyMaxAmount() != null
                    && monthlyAmount > product.getMonthlyMaxAmount())) {
                throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
            }
        }
    }

    private void validateEtfPolicy(
            SimulationSaveRequest request,
            Map<Long, SimulationProductRecord> candidates,
            RiskProfile riskProfile
    ) {
        for (SimulationSaveRequest.SelectedProduct selected : request.getProducts()) {
            if (selected.getRecommendationType() != ProductType.ETF) {
                continue;
            }
            String category = candidates.get(selected.getProductId()).getProductCategory();
            boolean allowed = switch (riskProfile) {
                case CONSERVATIVE -> "BOND_MIXED".equals(category);
                case BALANCED -> Set.of("BOND_MIXED", "DOMESTIC_INDEX", "FOREIGN_INDEX")
                        .contains(category);
                case AGGRESSIVE -> Set.of("DOMESTIC_INDEX", "FOREIGN_INDEX").contains(category);
            };
            if (!allowed) {
                throw new SimulationException(SimulationError.INVALID_SAVE_REQUEST);
            }
        }
    }

    private LocalDate resolveAsOfDate(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? LocalDate.now() : requestedDate;
        if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(2000, 1, 1))) {
            throw new SimulationException(SimulationError.INVALID_AS_OF_DATE);
        }
        return date;
    }

    private LocalDate resolveDeductionResetDate(LocalDate oldestGiftDate, LocalDate asOfDate) {
        LocalDate resetDate = oldestGiftDate == null
                ? asOfDate.plusYears(DEDUCTION_WINDOW_YEARS)
                : oldestGiftDate.plusYears(DEDUCTION_WINDOW_YEARS);
        while (!resetDate.isAfter(asOfDate)) {
            resetDate = resetDate.plusYears(DEDUCTION_WINDOW_YEARS);
        }
        return resetDate;
    }

    private LocalDate nextReleaseDate(List<GiftPoint> history, LocalDate afterDate) {
        return history.stream()
                .map(point -> point.date().plusYears(DEDUCTION_WINDOW_YEARS))
                .filter(date -> date.isAfter(afterDate))
                .min(LocalDate::compareTo)
                .orElse(afterDate.plusYears(DEDUCTION_WINDOW_YEARS));
    }

    private String executeFingerprint(SimulationExecuteRequest request, LocalDate asOfDate) {
        return String.join(
                "|",
                String.valueOf(request.getFamilyId()),
                String.valueOf(request.getRequestedAmount()),
                String.valueOf(request.getTaxPaymentMethod()),
                String.valueOf(request.getInvestmentPeriodMonths()),
                asOfDate.toString()
        );
    }

    private String saveFingerprint(Long simulationId, SimulationSaveRequest request) {
        String products = request.getProducts().stream()
                .sorted(Comparator.comparing(SimulationSaveRequest.SelectedProduct::getProductId))
                .map(product -> product.getProductId()
                        + ":" + product.getRecommendationType()
                        + ":" + product.getAllocatedAmount())
                .collect(Collectors.joining(","));
        return String.join(
                "|",
                String.valueOf(simulationId),
                String.valueOf(request.getVersion()),
                String.valueOf(request.getSelectedScenarioType()),
                String.valueOf(request.getResultId()),
                String.valueOf(request.getRiskProfile()),
                products,
                request.getClientCalculation() == null
                        ? ""
                        : request.getClientCalculation().getFormulaVersion()
                        + ":" + request.getClientCalculation().getExpectedFutureValue()
                        + ":" + request.getClientCalculation().getExpectedProfit()
        );
    }

    private long value(Long number) {
        return number == null ? 0L : number;
    }

    private long ratioAmount(long principal, BigDecimal ratio) {
        return BigDecimal.valueOf(principal)
                .multiply(ratio)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private long safeMultiply(long amount, int multiplier) {
        if (amount <= 0 || multiplier <= 0) {
            return 0;
        }
        if (amount > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return amount * multiplier;
    }

    private record ScenarioAggregate(
            SimulationResultRecord result,
            List<SimulationTrancheRecord> tranches,
            Map<ProductType, Long> defaultAllocationAmounts
    ) {
    }

    private record GiftPoint(LocalDate date, long amount) {
    }
}
