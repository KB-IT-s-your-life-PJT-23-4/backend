package com.example.project.admin.product.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class AdminProductPreferentialConditionResponse {
    private final Long preferentialInterestRateId;
    private final BigDecimal additionalRatePercent;
    private final String conditionCode;
    private final String preferentialCondition;
    private final LocalDate baseDate;
}