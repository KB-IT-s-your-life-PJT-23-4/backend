package com.example.project.simulation.service;

import com.example.project.simulation.domain.EtfPriceRecord;
import com.example.project.simulation.dto.response.EtfVolatilityResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class EtfVolatilityCalculator {

    public static final int MAX_PRICE_OBSERVATIONS = 253;

    private static final int MIN_PRICE_OBSERVATIONS = 3;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TRADING_DAYS_PER_YEAR = BigDecimal.valueOf(252);
    private static final BigDecimal MEDIUM_VOLATILITY_THRESHOLD = BigDecimal.TEN;
    private static final BigDecimal HIGH_VOLATILITY_THRESHOLD = BigDecimal.valueOf(20);
    private static final MathContext MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final String CALCULATION_BASIS = "DAILY_CLOSE_SAMPLE_STDDEV_252";

    public EtfVolatilityResponse calculate(
            List<EtfPriceRecord> priceRecords,
            String riskLevel
    ) {
        List<EtfPriceRecord> prices = priceRecords == null
                ? List.of()
                : priceRecords.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getBaseDate() != null)
                .filter(item -> item.getClosePrice() != null)
                .filter(item -> item.getClosePrice().signum() > 0)
                .sorted(Comparator.comparing(EtfPriceRecord::getBaseDate))
                .toList();
        if (prices.size() < MIN_PRICE_OBSERVATIONS) {
            return unavailable(prices, riskLevel);
        }

        List<BigDecimal> returns = java.util.stream.IntStream
                .range(1, prices.size())
                .mapToObj(index -> prices.get(index).getClosePrice()
                        .divide(
                                prices.get(index - 1).getClosePrice(),
                                MATH_CONTEXT
                        )
                        .subtract(BigDecimal.ONE, MATH_CONTEXT))
                .toList();
        BigDecimal returnCount = BigDecimal.valueOf(returns.size());
        BigDecimal mean = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(returnCount, MATH_CONTEXT);
        BigDecimal squaredDeviationSum = returns.stream()
                .map(value -> value.subtract(mean, MATH_CONTEXT))
                .map(value -> value.multiply(value, MATH_CONTEXT))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sampleVariance = squaredDeviationSum.divide(
                BigDecimal.valueOf(returns.size() - 1L),
                MATH_CONTEXT
        );
        BigDecimal annualizedVolatility = sampleVariance
                .sqrt(MATH_CONTEXT)
                .multiply(TRADING_DAYS_PER_YEAR.sqrt(MATH_CONTEXT), MATH_CONTEXT)
                .multiply(ONE_HUNDRED, MATH_CONTEXT)
                .setScale(2, RoundingMode.HALF_UP);
        String volatilityLevel = volatilityLevel(annualizedVolatility);

        return new EtfVolatilityResponse(
                true,
                annualizedVolatility,
                volatilityLevel,
                prices.size(),
                prices.get(0).getBaseDate(),
                prices.get(prices.size() - 1).getBaseDate(),
                CALCULATION_BASIS,
                riskNotice(riskLevel)
                        + " 변동성은 과거 종가를 연환산한 참고값이며 미래 변동을 보장하지 않아요."
        );
    }

    private EtfVolatilityResponse unavailable(
            List<EtfPriceRecord> prices,
            String riskLevel
    ) {
        return new EtfVolatilityResponse(
                false,
                null,
                "UNAVAILABLE",
                prices.size(),
                prices.isEmpty() ? null : prices.get(0).getBaseDate(),
                prices.isEmpty() ? null : prices.get(prices.size() - 1).getBaseDate(),
                CALCULATION_BASIS,
                "가격 이력이 3건 이상 필요해 변동성을 아직 계산할 수 없어요. "
                        + riskNotice(riskLevel)
        );
    }

    private String volatilityLevel(BigDecimal annualizedVolatility) {
        if (annualizedVolatility.compareTo(MEDIUM_VOLATILITY_THRESHOLD) < 0) {
            return "LOW";
        }
        if (annualizedVolatility.compareTo(HIGH_VOLATILITY_THRESHOLD) < 0) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private String riskNotice(String riskLevel) {
        if ("EX_LOW".equals(riskLevel) || "LOW".equals(riskLevel)) {
            return "비교적 낮은 위험등급이지만 시장 상황에 따라 원금 손실이 발생할 수 있어요.";
        }
        if ("HIGH".equals(riskLevel)) {
            return "높은 위험등급으로 가격 변동이 크고 투자 기간 중 손실이 발생할 수 있어요.";
        }
        if ("EX_HIGH".equals(riskLevel)) {
            return "매우 높은 위험등급으로 단기간에도 큰 폭의 손실이 발생할 수 있어요.";
        }
        return "중간 위험등급으로 가격 변동과 원금 손실 가능성을 함께 고려해 주세요.";
    }
}
