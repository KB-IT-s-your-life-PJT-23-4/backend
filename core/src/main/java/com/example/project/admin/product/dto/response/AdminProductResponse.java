package com.example.project.admin.product.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminProductResponse {
    private final Long productVersionId;
    private final Long productDataVersionId;
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final String productType;
    private final String description;
    private final String productUrl;
    private final String salesStatus;
    private final LocalDateTime createdAt;

    private final Long minAmount;
    private final Long maxAmount;
    private final Integer minMonth;
    private final Integer maxMonth;
    private final BigDecimal minBaseRatePercent;
    private final BigDecimal maxRatePercent;
    private final List<AdminProductRateTierResponse> rateTiers;

    private final String savingsCategory;
    private final Long monthlyMinAmount;
    private final Long monthlyMaxAmount;

    private final String stockCode;
    private final String etfCategory;
    private final String trackingIndex;
    private final BigDecimal annualReturn5yPercent;
    private final BigDecimal bondRatioPercent;
    private final String riskLevel;
}