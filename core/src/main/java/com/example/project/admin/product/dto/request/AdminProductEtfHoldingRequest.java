package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminProductEtfHoldingRequest {
    private Long holdingId;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer holdingRank;

    @NotBlank
    @Size(max = 200)
    private String holdingName;

    @Size(max = 50)
    private String holdingCode;

    @NotBlank
    private String assetType; // STOCK / BOND / ETF / FUTURES / CASH

    @Size(min = 2, max = 2)
    private String countryCode;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal weightPercent;

    private LocalDate baseDate;
}