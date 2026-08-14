package com.example.project.simulation.service;

import com.example.project.simulation.domain.CalculationType;
import com.example.project.simulation.domain.ProductCandidate;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxBracket;
import com.example.project.simulation.domain.TaxPaymentMethod;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

@Component
public class SimulationCalculator {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal FILING_TAX_CREDIT_RATE = new BigDecimal("0.03");
    private static final long MINIMUM_TAXABLE_BASE = 500_000L;
    private static final int MAX_GROSS_UP_ITERATIONS = 50;

    public TaxOutcome calculateTax(
            long giftAmount,
            long deductionAmount,
            TaxPaymentMethod paymentMethod,
            List<TaxBracket> brackets
    ) {
        return calculateTax(
                giftAmount,
                0L,
                deductionAmount,
                paymentMethod,
                brackets
        );
    }

    /**
     * 최근 10년 과거 증여와 이번 증여의 과세표준을 합산해 이번 증여의 증분세액을 계산한다.
     * 과거 증여분에 해당하는 산출세액은 빼서 동일 금액에 세금이 중복 부과되지 않게 한다.
     */
    public TaxOutcome calculateTax(
            long giftAmount,
            long previousGiftAmount,
            long deductionLimit,
            TaxPaymentMethod paymentMethod,
            List<TaxBracket> brackets
    ) {
        long normalizedGiftAmount = Math.max(0L, giftAmount);
        long normalizedPreviousGiftAmount = Math.max(0L, previousGiftAmount);
        long normalizedDeductionLimit = Math.max(0L, deductionLimit);
        long remainingDeduction = Math.max(
                0L,
                normalizedDeductionLimit - Math.min(
                        normalizedPreviousGiftAmount,
                        normalizedDeductionLimit
                )
        );
        long appliedDeduction = Math.min(normalizedGiftAmount, remainingDeduction);
        long previousTaxableAmount = Math.max(
                0L,
                normalizedPreviousGiftAmount - normalizedDeductionLimit
        );
        long currentTaxableAmount = Math.max(
                0L,
                normalizedGiftAmount - appliedDeduction
        );
        long previousCalculatedTax = calculateProgressiveTax(
                previousTaxableAmount,
                brackets
        );

        if (paymentMethod == TaxPaymentMethod.RECIPIENT_PAYS) {
            long cumulativeTaxableAmount = Math.addExact(
                    previousTaxableAmount,
                    currentTaxableAmount
            );
            long calculatedTax = Math.max(
                    0L,
                    calculateProgressiveTax(cumulativeTaxableAmount, brackets)
                            - previousCalculatedTax
            );
            long payableTax = calculatePayableTax(calculatedTax);
            return new TaxOutcome(
                    appliedDeduction,
                    currentTaxableAmount,
                    payableTax,
                    normalizedGiftAmount,
                    Math.max(0, normalizedGiftAmount - payableTax)
            );
        }

        long grossedUpTax = 0L;
        for (int index = 0; index < MAX_GROSS_UP_ITERATIONS; index++) {
            long taxableAmount = Math.addExact(currentTaxableAmount, grossedUpTax);
            long cumulativeTaxableAmount = Math.addExact(
                    previousTaxableAmount,
                    taxableAmount
            );
            long calculatedTax = Math.max(
                    0L,
                    calculateProgressiveTax(cumulativeTaxableAmount, brackets)
                            - previousCalculatedTax
            );
            long nextPayableTax = calculatePayableTax(calculatedTax);
            if (Math.abs(nextPayableTax - grossedUpTax) <= 1) {
                return new TaxOutcome(
                        appliedDeduction,
                        taxableAmount,
                        nextPayableTax,
                        Math.addExact(normalizedGiftAmount, nextPayableTax),
                        normalizedGiftAmount
                );
            }
            grossedUpTax = nextPayableTax;
        }

        throw new SimulationException(SimulationError.TAX_CALCULATION_NOT_CONVERGED);
    }

    public long calculateProgressiveTax(long taxableAmount, List<TaxBracket> brackets) {
        // 상속세 및 증여세법 제55조 제2항: 과세표준이 50만 원 미만이면 증여세를 부과하지 않는다.
        if (taxableAmount < MINIMUM_TAXABLE_BASE) {
            return 0;
        }

        TaxBracket bracket = brackets.stream()
                .filter(item -> taxableAmount > value(item.getLowerBound(), 0L))
                .filter(item -> item.getUpperBound() == null || taxableAmount <= item.getUpperBound())
                .findFirst()
                .orElseGet(() -> brackets.isEmpty() ? null : brackets.get(brackets.size() - 1));

        if (bracket == null) {
            throw new SimulationException(SimulationError.TAX_BRACKET_NOT_FOUND);
        }

        BigDecimal rate = bracket.getTaxRate() == null ? BigDecimal.ZERO : bracket.getTaxRate();
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            rate = rate.divide(ONE_HUNDRED, MC);
        }

        return Math.max(0, BigDecimal.valueOf(taxableAmount)
                .multiply(rate, MC)
                .subtract(BigDecimal.valueOf(value(bracket.getProgressiveDeduction(), 0L)))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue());
    }

    public long calculatePayableTax(long calculatedTax) {
        long normalizedTax = Math.max(0L, calculatedTax);
        long filingTaxCredit = BigDecimal.valueOf(normalizedTax)
                .multiply(FILING_TAX_CREDIT_RATE, MC)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return Math.max(0L, normalizedTax - filingTaxCredit);
    }

    public long calculateProductFutureValue(
            CalculationType calculationType,
            long principal,
            BigDecimal annualRatePercent,
            int months
    ) {
        if (principal <= 0 || months <= 0) {
            return Math.max(0, principal);
        }

        BigDecimal rate = annualRatePercent == null ? BigDecimal.ZERO : annualRatePercent;
        return switch (calculationType) {
            case SIMPLE_INTEREST -> calculateDeposit(principal, rate, months);
            case MONTHLY_INSTALLMENT -> calculateSavings(principal, rate, months);
            case COMPOUND_RETURN -> calculateEtf(principal, rate, months);
        };
    }

    public boolean canCoverWithReinvestment(
            int totalMonths,
            Integer minimumContractMonths,
            Integer maximumContractMonths
    ) {
        return !reinvestmentPeriods(
                totalMonths,
                minimumContractMonths,
                maximumContractMonths
        ).isEmpty();
    }

    public List<Integer> reinvestmentPeriods(
            int totalMonths,
            Integer minimumContractMonths,
            Integer maximumContractMonths
    ) {
        if (totalMonths <= 0
                || minimumContractMonths == null
                || maximumContractMonths == null
                || minimumContractMonths <= 0
                || maximumContractMonths < minimumContractMonths) {
            return List.of();
        }

        int minimumContractCount =
                (totalMonths + maximumContractMonths - 1) / maximumContractMonths;
        int maximumContractCount = totalMonths / minimumContractMonths;
        if (minimumContractCount > maximumContractCount) {
            return List.of();
        }

        int contractCount = minimumContractCount;
        int remainingMonths = totalMonths;
        List<Integer> periods = new ArrayList<>(contractCount);
        for (int index = 0; index < contractCount; index++) {
            int remainingContracts = contractCount - index - 1;
            int contractMonths = Math.min(
                    maximumContractMonths,
                    remainingMonths - remainingContracts * minimumContractMonths
            );
            if (contractMonths < minimumContractMonths
                    || contractMonths > maximumContractMonths) {
                return List.of();
            }
            periods.add(contractMonths);
            remainingMonths -= contractMonths;
        }
        return List.copyOf(periods);
    }

    /**
     * Builds the longest valid sequence of fixed-term contracts that does not
     * pass the evaluation date. Any period shorter than the product's minimum
     * term remains as cash instead of invalidating the whole simulation.
     */
    public ReinvestmentPlan reinvestmentPlan(
            int totalMonths,
            Integer minimumContractMonths,
            Integer maximumContractMonths
    ) {
        int safeTotalMonths = Math.max(0, totalMonths);
        if (safeTotalMonths == 0
                || minimumContractMonths == null
                || maximumContractMonths == null
                || minimumContractMonths <= 0
                || maximumContractMonths < minimumContractMonths) {
            return new ReinvestmentPlan(List.of(), safeTotalMonths);
        }

        for (int coveredMonths = safeTotalMonths;
             coveredMonths >= minimumContractMonths;
             coveredMonths--) {
            List<Integer> periods = reinvestmentPeriods(
                    coveredMonths,
                    minimumContractMonths,
                    maximumContractMonths
            );
            if (!periods.isEmpty()) {
                return new ReinvestmentPlan(
                        periods,
                        safeTotalMonths - coveredMonths
                );
            }
        }
        return new ReinvestmentPlan(List.of(), safeTotalMonths);
    }

    public long calculateReinvestedProductFutureValue(
            CalculationType calculationType,
            long principal,
            BigDecimal annualRatePercent,
            int totalMonths,
            Integer minimumContractMonths,
            Integer maximumContractMonths
    ) {
        return calculateReinvestedProductFutureValue(
                calculationType,
                principal,
                totalMonths,
                minimumContractMonths,
                maximumContractMonths,
                ignored -> annualRatePercent
        );
    }

    public long calculateReinvestedProductFutureValue(
            CalculationType calculationType,
            long principal,
            int totalMonths,
            Integer minimumContractMonths,
            Integer maximumContractMonths,
            IntFunction<BigDecimal> annualRateResolver
    ) {
        if (calculationType == CalculationType.COMPOUND_RETURN) {
            return calculateProductFutureValue(
                    calculationType,
                    principal,
                    annualRateResolver.apply(totalMonths),
                    totalMonths
            );
        }

        List<Integer> periods = reinvestmentPlan(
                totalMonths,
                minimumContractMonths,
                maximumContractMonths
        ).contractPeriods();

        long maturityValue = principal;
        for (Integer period : periods) {
            maturityValue = calculateProductFutureValue(
                    calculationType,
                    maturityValue,
                    annualRateResolver.apply(period),
                    period
            );
        }
        return maturityValue;
    }

    public List<Long> splitAllocatedAmountAcrossTranches(
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal
    ) {
        return List.copyOf(splitAcrossTranches(
                allocatedAmount,
                tranches,
                investmentPrincipal
        ));
    }

    public long calculateSelectedProductValue(
            SimulationProductRecord product,
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate evaluationDate
    ) {
        return calculateSelectedProductValue(
                product,
                allocatedAmount,
                tranches,
                investmentPrincipal,
                evaluationDate,
                ignored -> product.getAppliedAnnualRatePercent()
        );
    }

    public long calculateSelectedProductValue(
            SimulationProductRecord product,
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate evaluationDate,
            IntFunction<BigDecimal> annualRateResolver
    ) {
        if (allocatedAmount <= 0 || investmentPrincipal <= 0) {
            return 0;
        }

        List<Long> portions = splitAcrossTranches(allocatedAmount, tranches, investmentPrincipal);
        long total = 0;

        for (int index = 0; index < tranches.size(); index++) {
            SimulationTrancheRecord tranche = tranches.get(index);
            if (tranche.getGiftDate().isAfter(evaluationDate)) {
                continue;
            }
            int months = remainingMonths(tranche.getGiftDate(), evaluationDate);
            total += calculateReinvestedProductFutureValue(
                    product.calculationType(),
                    portions.get(index),
                    months,
                    product.getMinimumContractMonths(),
                    product.getMaximumContractMonths(),
                    annualRateResolver
            );
        }

        return total;
    }

    public long calculateDefaultPortfolioValue(
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate evaluationDate,
            Map<ProductType, BigDecimal> allocation,
            Map<ProductType, ProductCandidate> defaults
    ) {
        long total = 0;
        long allocatedSoFar = 0;
        ProductType[] types = ProductType.values();

        for (int index = 0; index < types.length; index++) {
            ProductType type = types[index];
            ProductCandidate product = defaults.get(type);
            if (product == null) {
                throw new SimulationException(SimulationError.PRODUCT_DATA_NOT_READY);
            }

            long allocatedAmount;
            if (index == types.length - 1) {
                allocatedAmount = investmentPrincipal - allocatedSoFar;
            } else {
                allocatedAmount = BigDecimal.valueOf(investmentPrincipal)
                        .multiply(allocation.get(type), MC)
                        .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
                        .longValue();
                allocatedSoFar += allocatedAmount;
            }

            SimulationProductRecord snapshot = new SimulationProductRecord();
            snapshot.setProductType(type);
            snapshot.setAppliedAnnualRatePercent(product.getAppliedAnnualRatePercent());
            snapshot.setMinimumContractMonths(product.getMinMonth());
            snapshot.setMaximumContractMonths(product.getMaxMonth());
            total += calculateSelectedProductValue(
                    snapshot,
                    allocatedAmount,
                    tranches,
                    investmentPrincipal,
                    evaluationDate
            );
        }

        return total;
    }

    public int remainingMonths(LocalDate startDate, LocalDate evaluationDate) {
        if (!startDate.isBefore(evaluationDate)) {
            return 0;
        }
        return Math.max(0, Math.toIntExact(ChronoUnit.MONTHS.between(startDate, evaluationDate)));
    }

    public record ReinvestmentPlan(
            List<Integer> contractPeriods,
            int cashHoldingMonths
    ) {
        public ReinvestmentPlan {
            contractPeriods = List.copyOf(contractPeriods);
        }

        public int investedMonths() {
            return contractPeriods.stream().mapToInt(Integer::intValue).sum();
        }

        public boolean hasInvestmentContract() {
            return !contractPeriods.isEmpty();
        }
    }

    private long calculateDeposit(long principal, BigDecimal annualRatePercent, int months) {
        BigDecimal interest = BigDecimal.valueOf(principal)
                .multiply(annualRatePercent.divide(ONE_HUNDRED, MC), MC)
                .multiply(BigDecimal.valueOf(months).divide(TWELVE, MC), MC);
        return BigDecimal.valueOf(principal)
                .add(interest)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private long calculateSavings(long totalContribution, BigDecimal annualRatePercent, int months) {
        BigDecimal monthlyContribution = BigDecimal.valueOf(totalContribution)
                .divide(BigDecimal.valueOf(months), MC);
        BigDecimal monthlyRate = annualRatePercent
                .divide(ONE_HUNDRED, MC)
                .divide(TWELVE, MC);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return totalContribution;
        }

        BigDecimal growth = BigDecimal.ONE.add(monthlyRate, MC).pow(months, MC);
        BigDecimal annuityFactor = growth.subtract(BigDecimal.ONE)
                .divide(monthlyRate, MC);

        return monthlyContribution
                .multiply(annuityFactor, MC)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private long calculateEtf(long principal, BigDecimal annualRatePercent, int months) {
        double annualGrowth = Math.max(
                0,
                1 + annualRatePercent.divide(ONE_HUNDRED, MC).doubleValue()
        );
        double value = principal * Math.pow(annualGrowth, months / 12.0);
        return Math.round(value);
    }

    private List<Long> splitAcrossTranches(
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal
    ) {
        List<Long> portions = new ArrayList<>(tranches.size());
        long assigned = 0;

        for (int index = 0; index < tranches.size(); index++) {
            long portion;
            if (index == tranches.size() - 1) {
                portion = allocatedAmount - assigned;
            } else {
                portion = BigDecimal.valueOf(allocatedAmount)
                        .multiply(BigDecimal.valueOf(tranches.get(index).getInvestmentAmount()), MC)
                        .divide(BigDecimal.valueOf(investmentPrincipal), 0, RoundingMode.HALF_UP)
                        .longValue();
                assigned += portion;
            }
            portions.add(Math.max(0, portion));
        }

        return portions;
    }

    private long value(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    public record TaxOutcome(
            long deductionAmount,
            long taxableAmount,
            long giftTax,
            long donorRequiredAmount,
            long investmentAmount
    ) {
    }
}
