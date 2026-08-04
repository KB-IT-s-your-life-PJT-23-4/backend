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
            result.put(RiskProfile.CONSERVATIVE, ratios(45, 45, 10));
            result.put(RiskProfile.BALANCED, ratios(40, 40, 20));
            result.put(RiskProfile.AGGRESSIVE, ratios(35, 35, 30));
        } else if (years < 10) {
            result.put(RiskProfile.CONSERVATIVE, ratios(40, 40, 20));
            result.put(RiskProfile.BALANCED, ratios(35, 35, 30));
            result.put(RiskProfile.AGGRESSIVE, ratios(25, 25, 50));
        } else {
            result.put(RiskProfile.CONSERVATIVE, ratios(40, 40, 20));
            result.put(RiskProfile.BALANCED, ratios(30, 30, 40));
            result.put(RiskProfile.AGGRESSIVE, ratios(20, 20, 60));
        }

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

    private static Map<ProductType, BigDecimal> ratios(int deposit, int savings, int etf) {
        Map<ProductType, BigDecimal> result = new EnumMap<>(ProductType.class);
        result.put(ProductType.DEPOSIT, BigDecimal.valueOf(deposit));
        result.put(ProductType.SAVINGS, BigDecimal.valueOf(savings));
        result.put(ProductType.ETF, BigDecimal.valueOf(etf));
        return result;
    }
}
