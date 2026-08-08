package com.example.project.admin.product.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class AdminProductEtfHoldingResponse {
    private final Long holdingId;
    private final Integer holdingRank;
    private final String holdingName;
    private final String holdingCode;
    private final String assetType;
    private final String countryCode;
    private final BigDecimal weightPercent;
    private final LocalDate baseDate;
}