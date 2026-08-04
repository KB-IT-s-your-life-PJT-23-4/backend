package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVersionDetailRecord {
    private Long productVersionId;
    private Long productDataVersionId;
    private Long productId;
    private String productCode;
    private String productName;
    private ProductType productType;
    private String description;
    private String productUrl;
    private String salesStatus;
    private Long minimumAmount;
    private Long maximumAmount;
    private Integer minimumMonths;
    private Integer maximumMonths;
    private String savingsCategory;
    private Long monthlyMinimumAmount;
    private Long monthlyMaximumAmount;
    private String stockCode;
    private String etfCategory;
    private String trackingIndex;
    private BigDecimal annualizedReturn5yPercent;
    private BigDecimal bondRatioPercent;
    private String riskLevel;
}
