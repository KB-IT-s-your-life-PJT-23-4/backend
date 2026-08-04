package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BaseRateRecord {
    private Long baseInterestRateId;
    private Long productVersionId;
    private Integer minimumMonths;
    private Integer maximumMonths;
    private BigDecimal baseRatePercent;
    private BigDecimal maximumRatePercent;
}
