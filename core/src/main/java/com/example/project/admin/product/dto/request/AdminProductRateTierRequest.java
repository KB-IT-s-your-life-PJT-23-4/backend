package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminProductRateTierRequest {
    private Long baseInterestRateId;

    @NotNull
    private Integer minMonth;

    private Integer maxMonth;

    @NotNull
    @DecimalMin("0")
    private BigDecimal baseRatePercent;

    @NotNull
    @DecimalMin("0")
    private BigDecimal maxRatePercent;
}