package com.example.project.gift.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxBracketVO {

    private Long bracketId;

    private BigDecimal taxRate;
    // 정밀 계산용으로 bigdecimal 씀

    private Long progressiveDeduction;
    // 누진 공제액
}
