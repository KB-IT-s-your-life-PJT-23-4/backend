package com.example.project.simulation.service;

import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

public final class PortfolioPolicy {

    private static final BigDecimal RATIO_TOLERANCE = new BigDecimal("0.05");

    private PortfolioPolicy() {
    }

    public static Map<RiskProfile, Map<ProductType, BigDecimal>> allocations(int months) {
        int years = Math.max(1, (int) Math.ceil(months / 12.0));
        Map<RiskProfile, Map<ProductType, BigDecimal>> result = new EnumMap<>(RiskProfile.class);

        if (years <= 3) {
            result.put(RiskProfile.CONSERVATIVE, ratios(0, 90, 10));
            result.put(RiskProfile.BALANCED, ratios(0, 80, 20));
            result.put(RiskProfile.AGGRESSIVE, ratios(0, 60, 40));
        } else if (years < 10) {
            result.put(RiskProfile.CONSERVATIVE, ratios(0, 80, 20));
            result.put(RiskProfile.BALANCED, ratios(0, 70, 30));
            result.put(RiskProfile.AGGRESSIVE, ratios(0, 50, 50));
        } else {
            result.put(RiskProfile.CONSERVATIVE, ratios(0, 80, 20));
            result.put(RiskProfile.BALANCED, ratios(0, 60, 40));
            result.put(RiskProfile.AGGRESSIVE, ratios(0, 40, 60));
        }

        return result;
    }

    static Map<ProductType, Long> allocateSavingsFirst(
            long investmentPrincipal,
            Map<ProductType, BigDecimal> targetRatios,
            long savingsCapacity
    ) {
        long principal = Math.max(0, investmentPrincipal);
        long etfAmount = ratioAmount(
                principal,
                targetRatios.getOrDefault(ProductType.ETF, BigDecimal.ZERO)
        );
        long safeAssetAmount = Math.max(0, principal - etfAmount);
        long savingsAmount = Math.min(safeAssetAmount, Math.max(0, savingsCapacity));
        long depositAmount = safeAssetAmount - savingsAmount;

        Map<ProductType, Long> result = new EnumMap<>(ProductType.class);
        result.put(ProductType.DEPOSIT, depositAmount);
        result.put(ProductType.SAVINGS, savingsAmount);
        result.put(ProductType.ETF, etfAmount);
        return result;
    }

    public static RiskProfile resolveProfile(
            Map<ProductType, Long> allocatedByType,
            long investmentPrincipal,
            int months,
            RiskProfile requestedProfile
    ) {
        if (investmentPrincipal <= 0) {
            throw new SimulationException(SimulationError.PORTFOLIO_ALLOCATION_MISMATCH);
        }

        Map<RiskProfile, Map<ProductType, BigDecimal>> policies = allocations(months);
        if (requestedProfile != null) {
            if (!matches(allocatedByType, investmentPrincipal, policies.get(requestedProfile))) {
                throw new SimulationException(SimulationError.PORTFOLIO_ALLOCATION_MISMATCH);
            }
            return requestedProfile;
        }

        return policies.entrySet().stream()
                .filter(entry -> matches(allocatedByType, investmentPrincipal, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new SimulationException(
                        SimulationError.PORTFOLIO_ALLOCATION_MISMATCH));
    }

    private static boolean matches(
            Map<ProductType, Long> allocatedByType,
            long principal,
            Map<ProductType, BigDecimal> expected
    ) {
        long safeAssetAmount =
                allocatedByType.getOrDefault(ProductType.DEPOSIT, 0L)
                        + allocatedByType.getOrDefault(ProductType.SAVINGS, 0L);
        BigDecimal actualSafeAssetRatio = ratio(safeAssetAmount, principal);
        BigDecimal expectedSafeAssetRatio = expected.get(ProductType.DEPOSIT)
                .add(expected.get(ProductType.SAVINGS));
        BigDecimal actualEtfRatio = ratio(
                allocatedByType.getOrDefault(ProductType.ETF, 0L),
                principal
        );

        return actualSafeAssetRatio.subtract(expectedSafeAssetRatio).abs()
                .compareTo(RATIO_TOLERANCE) <= 0
                && actualEtfRatio.subtract(expected.get(ProductType.ETF)).abs()
                .compareTo(RATIO_TOLERANCE) <= 0;
    }

    private static BigDecimal ratio(long amount, long principal) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(principal), 4, RoundingMode.HALF_UP);
    }

    private static long ratioAmount(long principal, BigDecimal ratio) {
        return BigDecimal.valueOf(principal)
                .multiply(ratio)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static Map<ProductType, BigDecimal> ratios(int deposit, int savings, int etf) {
        Map<ProductType, BigDecimal> result = new EnumMap<>(ProductType.class);
        result.put(ProductType.DEPOSIT, BigDecimal.valueOf(deposit));
        result.put(ProductType.SAVINGS, BigDecimal.valueOf(savings));
        result.put(ProductType.ETF, BigDecimal.valueOf(etf));
        return result;
    }
}
