package com.example.project.admin.product.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminProductRow {
    private Long productVersionId;
    private Long productDataVersionId;
    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private String description;
    private String productUrl;
    private String salesStatus;
    private LocalDateTime createdAt;

    // deposit / savings 공통
    private Long minAmount;
    private Long maxAmount;
    private Integer minMonth;
    private Integer maxMonth;
    private BigDecimal minBaseRatePercent;
    private BigDecimal maxRatePercent;

    // savings 전용
    private String savingsCategory;
    private Long monthlyMinAmount;
    private Long monthlyMaxAmount;

    // etf 전용
    private String stockCode;
    private String etfCategory;
    private String trackingIndex;
    private BigDecimal annualReturn10yPercent;
    private BigDecimal bondRatioPercent;
    private String riskLevel;
}
