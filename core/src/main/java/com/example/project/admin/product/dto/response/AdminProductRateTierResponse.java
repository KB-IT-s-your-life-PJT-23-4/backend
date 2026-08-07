package com.example.project.admin.product.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class AdminProductRateTierResponse {
    private final Long baseInterestRateId;
    private final Integer minMonth;
    private final Integer maxMonth;
    private final BigDecimal baseRatePercent;
    private final BigDecimal maxRatePercent;
    private final LocalDate baseDate;
}