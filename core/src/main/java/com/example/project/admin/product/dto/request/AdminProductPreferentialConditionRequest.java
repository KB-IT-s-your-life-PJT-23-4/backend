package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminProductPreferentialConditionRequest {
    private Long preferentialInterestRateId;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal additionalRatePercent;

    @NotBlank
    @Size(max = 50)
    private String conditionCode;

    @NotBlank
    @Size(max = 500)
    private String preferentialCondition;

    private LocalDate baseDate;
}