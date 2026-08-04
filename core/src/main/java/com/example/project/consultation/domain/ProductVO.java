package com.example.project.consultation.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ProductVO {

    private Long productId;
    private String productName;
    private String productType;        // DEPOSIT, SAVINGS, ETF
    private BigDecimal baseRatePercent; // 최단 기간 기본금리
    private String preferentialCondition; // 우대조건 여러 개는 콤마로 연결
}