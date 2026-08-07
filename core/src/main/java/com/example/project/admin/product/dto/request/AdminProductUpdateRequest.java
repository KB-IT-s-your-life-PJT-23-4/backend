package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdminProductUpdateRequest {

    @NotBlank
    @Size(max = 200)
    private String productName;

    @Size(max = 4000)
    private String description;

    @Size(max = 500)
    private String productUrl;

    @NotBlank
    private String salesStatus;

    // deposit / savings 공통
    private Long minAmount;
    private Long maxAmount;
    private Integer minMonth;
    private Integer maxMonth;

    // savings 전용
    private String savingsCategory;
    private Long monthlyMinAmount;
    private Long monthlyMaxAmount;

    // etf 전용
    private String stockCode;
    private String etfCategory;
    private String trackingIndex;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal bondRatioPercent;
    private String riskLevel;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal annualReturn5yPercent;

    private List<AdminProductRateTierRequest> rateTiers;

    private List<AdminProductPreferentialConditionRequest> preferentialConditions; // deposit/savings
    private List<AdminProductEtfHoldingRequest> etfHoldings; // etf

    private LocalDate rateBaseDate;
}