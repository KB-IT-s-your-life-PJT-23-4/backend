package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SimulationProductRecord {
    private Long simulationProductId;
    private Long resultId;
    private Long productId;
    private String productName;
    private ProductType productType;
    private String productCategory;
    private boolean selected;
    private Long defaultAllocatedAmount;
    private BigDecimal defaultAllocationRatio;
    private Long defaultExpectedFutureValue;
    private Long defaultExpectedProfit;
    private Long allocatedAmount;
    private BigDecimal allocationRatio;
    private BigDecimal minAnnualRatePercent;
    private BigDecimal maxAnnualRatePercent;
    private BigDecimal appliedAnnualRatePercent;
    private CalculationType calculationType;
    private Long expectedFutureValue;
    private Long expectedProfit;
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
}
