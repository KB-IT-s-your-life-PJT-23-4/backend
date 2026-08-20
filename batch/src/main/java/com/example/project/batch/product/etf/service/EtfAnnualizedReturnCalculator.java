package com.example.project.batch.product.etf.service;

import com.example.project.batch.product.etf.domain.EtfPricePoint;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;

@Component
public class EtfAnnualizedReturnCalculator {

    private static final double DAYS_PER_YEAR = 365.2425d;

    public BigDecimal calculate(EtfPricePoint start, EtfPricePoint end) {
        if (start == null || end == null || start.getClosePrice() == null || end.getClosePrice() == null) {
            throw new IllegalArgumentException("ETF 시작·종료 종가가 필요합니다.");
        }
        if (!end.getBaseDate().isAfter(start.getBaseDate())) {
            throw new IllegalArgumentException("ETF 종료 기준일은 시작 기준일보다 뒤여야 합니다.");
        }
        if (start.getClosePrice().signum() <= 0 || end.getClosePrice().signum() <= 0) {
            throw new IllegalArgumentException("ETF 종가는 0보다 커야 합니다.");
        }

        long elapsedDays = ChronoUnit.DAYS.between(start.getBaseDate(), end.getBaseDate());
        double elapsedYears = elapsedDays / DAYS_PER_YEAR;
        double priceRatio = end.getClosePrice()
                .divide(start.getClosePrice(), 16, RoundingMode.HALF_UP)
                .doubleValue();
        double annualizedRatePercent = (Math.pow(priceRatio, 1.0d / elapsedYears) - 1.0d) * 100.0d;

        if (!Double.isFinite(annualizedRatePercent)) {
            throw new IllegalArgumentException("ETF 연환산 수익률을 계산할 수 없습니다.");
        }
        return BigDecimal.valueOf(annualizedRatePercent).setScale(4, RoundingMode.HALF_UP);
    }
}
