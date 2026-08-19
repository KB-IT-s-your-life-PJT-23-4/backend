package com.example.project.batch.product.etf.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EtfProductTarget {
    private Long productId;
    private String stockCode;
    private String productName;
    private BigDecimal annualReturn10yPercent;
}
