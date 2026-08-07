package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class AdminProductRateTierRequest {
    private Long baseInterestRateId;

    @NotNull
    private Integer minMonth;

    private Integer maxMonth;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal baseRatePercent;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal maxRatePercent;
}