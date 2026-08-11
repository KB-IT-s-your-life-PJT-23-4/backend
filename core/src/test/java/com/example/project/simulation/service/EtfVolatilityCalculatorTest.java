package com.example.project.simulation.service;

import com.example.project.simulation.domain.EtfPriceRecord;
import com.example.project.simulation.dto.response.EtfVolatilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtfVolatilityCalculatorTest {

    private final EtfVolatilityCalculator calculator =
            new EtfVolatilityCalculator();

    @Test
    @DisplayName("일별 종가 수익률의 표본표준편차를 연환산한다")
    void calculateAnnualizedVolatilityFromDailyClosingPrices() {
        EtfVolatilityResponse result = calculator.calculate(
                List.of(
                        price("2026-08-03", "100.0000"),
                        price("2026-08-04", "102.0000"),
                        price("2026-08-05", "99.0000"),
                        price("2026-08-06", "103.0000")
                ),
                "HIGH"
        );

        assertTrue(result.isAvailable());
        assertNotNull(result.getAnnualizedVolatilityPercent());
        assertTrue(result.getAnnualizedVolatilityPercent().signum() > 0);
        assertEquals("HIGH", result.getVolatilityLevel());
        assertEquals(4, result.getPriceObservationCount());
        assertEquals(LocalDate.of(2026, 8, 3), result.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 6), result.getEndDate());
        assertTrue(result.getNotice().contains("높은 위험등급"));
    }

    @Test
    @DisplayName("종가 이력이 세 건 미만이면 변동성을 계산하지 않는다")
    void returnUnavailableWhenPriceHistoryIsInsufficient() {
        EtfVolatilityResponse result = calculator.calculate(
                List.of(price("2026-08-04", "100.0000")),
                "EX_HIGH"
        );

        assertFalse(result.isAvailable());
        assertEquals("UNAVAILABLE", result.getVolatilityLevel());
        assertEquals(1, result.getPriceObservationCount());
        assertTrue(result.getNotice().contains("3건 이상"));
        assertTrue(result.getNotice().contains("큰 폭의 손실"));
    }

    private EtfPriceRecord price(String date, String closePrice) {
        EtfPriceRecord record = new EtfPriceRecord();
        record.setProductId(1L);
        record.setBaseDate(LocalDate.parse(date));
        record.setClosePrice(new BigDecimal(closePrice));
        return record;
    }
}
