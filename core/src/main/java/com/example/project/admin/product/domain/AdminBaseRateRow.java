package com.example.project.admin.product.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminBaseRateRow {
    private Integer minMonth;
    private Integer maxMonth;
    private BigDecimal baseRatePercent;
    private BigDecimal maxRatePercent;
    private LocalDate baseDate;
    private Long baseInterestRateId;
}