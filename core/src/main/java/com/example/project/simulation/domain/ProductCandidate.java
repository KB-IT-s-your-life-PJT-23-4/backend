package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProductCandidate {
    private Long productId;
    private String productName;
    private ProductType productType;
    private String productCategory;
    private BigDecimal minAnnualRatePercent;
    private BigDecimal maxAnnualRatePercent;
    private BigDecimal appliedAnnualRatePercent;
    private Integer minMonth;
    private Integer maxMonth;
    private Long minAmount;
    private Long maxAmount;
    private Long monthlyMinAmount;
    private Long monthlyMaxAmount;
    private String preferentialConditions;
    private String trackingIndex;
    private String marketCapitalization;
    private BigDecimal dividendYieldPercent;
    private String riskLevel;
    private String productDetailUrl;
    private LocalDate productDataDate;

    public CalculationType calculationType() {
        return switch (productType) {
            case DEPOSIT -> CalculationType.DEPOSIT_SIMPLE_INTEREST;
            case SAVINGS -> CalculationType.SAVINGS_MONTHLY_INSTALLMENT;
            case ETF -> CalculationType.ETF_COMPOUND_RETURN;
        };
    }
}
