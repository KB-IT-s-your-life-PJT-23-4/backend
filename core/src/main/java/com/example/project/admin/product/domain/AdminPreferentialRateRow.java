package com.example.project.admin.product.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminPreferentialRateRow {
    private Long preferentialInterestRateId;
    private BigDecimal additionalRatePercent;
    private String conditionCode;
    private String preferentialCondition;
    private LocalDate baseDate;
}