package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Size;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdminProductCreateRequest {

    @NotBlank
    @Size(max = 50)
    private String productCode;

    @NotBlank
    private String productType; // DEPOSIT / SAVINGS / ETF

    @NotBlank
    @Size(max = 200)
    private String productName;

    @Size(max = 4000)
    private String description;

    @Size(max = 500)
    private String productUrl;

    @NotBlank
    private String salesStatus;

    private Long minAmount;
    private Long maxAmount;
    private Integer minMonth;
    private Integer maxMonth;

    private String savingsCategory;
    private Long monthlyMinAmount;
    private Long monthlyMaxAmount;

    private String stockCode;
    private String etfCategory;
    private String trackingIndex;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal annualReturn10yPercent;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal bondRatioPercent;
    private String riskLevel;

    private List<AdminProductRateTierRequest> rateTiers;

    private List<AdminProductPreferentialConditionRequest> preferentialConditions; // deposit/savings
    private List<AdminProductEtfHoldingRequest> etfHoldings; // etf

    private LocalDate rateBaseDate;
}