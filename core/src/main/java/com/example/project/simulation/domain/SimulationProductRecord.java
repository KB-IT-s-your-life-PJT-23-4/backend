package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SimulationProductRecord {
    private Long simulationProductId;
    private Long portfolioId;
    private Long productVersionId;
    private Long productId;
    private String productCode;
    private String productName;
    private ProductType productType;
    private String productCategory;
    private boolean selected;
    private Long allocatedAmount;
    private Long monthlyContributionAmount;
    private BigDecimal baseAnnualRatePercent;
    private BigDecimal maximumAnnualRatePercent;
    private BigDecimal appliedAnnualRatePercent;
    private Long expectedFutureValue;
    private List<PreferentialRateRecord> selectedPreferentialConditions;
    private LocalDateTime createdAt;

    public CalculationType calculationType() {
        return switch (productType) {
            case DEPOSIT -> CalculationType.SIMPLE_INTEREST;
            case SAVINGS -> CalculationType.MONTHLY_INSTALLMENT;
            case ETF -> CalculationType.COMPOUND_RETURN;
        };
    }
}
