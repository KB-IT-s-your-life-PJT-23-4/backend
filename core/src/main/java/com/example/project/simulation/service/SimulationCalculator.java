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

@Component
public class SimulationCalculator {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int MAX_GROSS_UP_ITERATIONS = 50;

    public TaxOutcome calculateTax(
            long giftAmount,
            long deductionAmount,
            TaxPaymentMethod paymentMethod,
            List<TaxBracket> brackets
    ) {
        long appliedDeduction = Math.max(0, Math.min(giftAmount, deductionAmount));

        if (paymentMethod == TaxPaymentMethod.RECIPIENT_PAYS) {
            long taxableAmount = Math.max(0, giftAmount - appliedDeduction);
            long tax = calculateProgressiveTax(taxableAmount, brackets);
            return new TaxOutcome(
                    appliedDeduction,
                    taxableAmount,
                    tax,
                    giftAmount,
                    Math.max(0, giftAmount - tax)
            );
        }

        long previousTax = 0;
        for (int index = 0; index < MAX_GROSS_UP_ITERATIONS; index++) {
            long taxableAmount = Math.max(0, giftAmount + previousTax - appliedDeduction);
            long nextTax = calculateProgressiveTax(taxableAmount, brackets);
            if (Math.abs(nextTax - previousTax) <= 1) {
                return new TaxOutcome(
                        appliedDeduction,
                        taxableAmount,
                        nextTax,
                        giftAmount + nextTax,
                        giftAmount
                );
            }
            previousTax = nextTax;
        }

        throw new SimulationException(SimulationError.TAX_CALCULATION_NOT_CONVERGED);
    }

    public long calculateProgressiveTax(long taxableAmount, List<TaxBracket> brackets) {
        if (taxableAmount <= 0) {
            return 0;
        }

        TaxBracket bracket = brackets.stream()
                .filter(item -> taxableAmount >= value(item.getLowerBound(), 0L))
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
        int baseMonths = totalMonths / contractCount;
        int remainder = totalMonths % contractCount;
        if (baseMonths < minimumContractMonths
                || baseMonths > maximumContractMonths
                || (remainder > 0 && baseMonths + 1 > maximumContractMonths)) {
            return List.of();
        }

        List<Integer> periods = new ArrayList<>(contractCount);
        for (int index = 0; index < contractCount; index++) {
            periods.add(baseMonths + (index < remainder ? 1 : 0));
        }
        return List.copyOf(periods);
    }

    public long calculateReinvestedProductFutureValue(
            CalculationType calculationType,
            long principal,
            BigDecimal annualRatePercent,
            int totalMonths,
            Integer minimumContractMonths,
            Integer maximumContractMonths
    ) {
        if (calculationType == CalculationType.COMPOUND_RETURN) {
            return calculateProductFutureValue(
                    calculationType,
                    principal,
                    annualRatePercent,
                    totalMonths
            );
        }

        List<Integer> periods = reinvestmentPeriods(
                totalMonths,
                minimumContractMonths,
                maximumContractMonths
        );
        if (periods.isEmpty()) {
            throw new SimulationException(SimulationError.PRODUCT_LIMIT_EXCEEDED);
        }

        long maturityValue = principal;
        for (Integer period : periods) {
            maturityValue = calculateProductFutureValue(
                    calculationType,
                    maturityValue,
                    annualRatePercent,
                    period
            );
        }
        return maturityValue;
    }

    public long calculateSelectedProductValue(
            SimulationProductRecord product,
            long allocatedAmount,
            List<SimulationTrancheRecord> tranches,
            long investmentPrincipal,
            LocalDate evaluationDate
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
                    product.getAppliedAnnualRatePercent(),
                    months,
                    product.getMinimumContractMonths(),
                    product.getMaximumContractMonths()
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

    private long calculateDeposit(long principal, BigDecimal annualRatePercent, int months) {
        BigDecimal interest = BigDecimal.valueOf(principal)
                .multiply(annualRatePercent.divide(ONE_HUNDRED, MC), MC)
                .multiply(BigDecimal.valueOf(months).divide(TWELVE, MC), MC);
        return BigDecimal.valueOf(principal)
                .add(interest)
                .setScale(0, RoundingMode.FLOOR)
                .longValue();
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
                .setScale(0, RoundingMode.FLOOR)
                .longValue();
    }

    private long calculateEtf(long principal, BigDecimal annualRatePercent, int months) {
        double annualGrowth = Math.max(
                0,
                1 + annualRatePercent.divide(ONE_HUNDRED, MC).doubleValue()
        );
        double value = principal * Math.pow(annualGrowth, months / 12.0);
        return (long) Math.floor(value);
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
