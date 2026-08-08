package com.example.project.admin.product.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminEtfHoldingRow {
    private Long holdingId;
    private Integer holdingRank;
    private String holdingName;
    private String holdingCode;
    private String assetType;
    private String countryCode;
    private BigDecimal weightPercent;
    private LocalDate baseDate;
}