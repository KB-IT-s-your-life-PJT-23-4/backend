package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EtfHoldingRecord {
    private Long holdingId;
    private Long productVersionId;
    private Integer rank;
    private String holdingName;
    private String holdingCode;
    private String assetType;
    private String countryCode;
    private BigDecimal weightPercent;
}
