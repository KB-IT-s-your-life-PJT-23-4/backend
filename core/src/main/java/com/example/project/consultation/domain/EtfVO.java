package com.example.project.consultation.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class EtfVO {
    private Long productId;
    private String productName;
    private String trackingIndex;
    private BigDecimal annualReturn5y;
}