package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PreferentialRateRecord {
    private Long preferentialInterestRateId;
    private Long productVersionId;
    private BigDecimal additionalRatePercent;
    private String conditionCode;
    private String description;
}
