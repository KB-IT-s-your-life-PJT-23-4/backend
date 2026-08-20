package com.example.project.batch.product.etf.service;

import com.example.project.batch.product.etf.domain.EtfPricePoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EtfAnnualizedReturnCalculatorTest {

    private final EtfAnnualizedReturnCalculator calculator = new EtfAnnualizedReturnCalculator();

    @Test
    void calculatesTenYearAnnualizedReturn() {
        EtfPricePoint start = price("2016-01-04", "10000");
        EtfPricePoint end = price("2026-01-05", "20000");

        BigDecimal result = calculator.calculate(start, end);

        assertEquals(new BigDecimal("7.1741"), result);
    }

    @Test
    void rejectsNonPositivePrice() {
        EtfPricePoint start = price("2016-01-04", "0");
        EtfPricePoint end = price("2026-01-05", "20000");

        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(start, end));
    }

    private EtfPricePoint price(String date, String closePrice) {
        EtfPricePoint point = new EtfPricePoint();
        point.setBaseDate(LocalDate.parse(date));
        point.setClosePrice(new BigDecimal(closePrice));
        return point;
    }
}
