package com.example.project.simulation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class EtfVolatilityResponse {
    private final boolean available;
    private final BigDecimal annualizedVolatilityPercent;
    private final String volatilityLevel;
    private final int priceObservationCount;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String calculationBasis;
    private final String notice;
}
