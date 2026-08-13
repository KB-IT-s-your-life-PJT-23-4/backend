package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProductCandidate {
    private Long productVersionId;
    private Long productDataVersionId;
    private Long productId;
    private String productCode;
    private String productName;
    private ProductType productType;
    private String productCategory;
    private String description;
    private String productUrl;
    private String salesStatus;
    private BigDecimal baseAnnualRatePercent;
    private BigDecimal maximumAnnualRatePercent;
    private BigDecimal appliedAnnualRatePercent;
    private Integer minMonth;
    private Integer maxMonth;
    private Long minAmount;
    private Long maxAmount;
    private Long monthlyMinAmount;
    private Long monthlyMaxAmount;
    private String trackingIndex;
    private BigDecimal annualizedReturn10yPercent;
    private BigDecimal bondRatioPercent;
    private String riskLevel;
    private LocalDate productDataDate;
    private List<BaseRateRecord> baseRateTiers;

    public CalculationType calculationType() {
        return switch (productType) {
            case DEPOSIT -> CalculationType.SIMPLE_INTEREST;
            case SAVINGS -> CalculationType.MONTHLY_INSTALLMENT;
            case ETF -> CalculationType.COMPOUND_RETURN;
        };
    }
}
