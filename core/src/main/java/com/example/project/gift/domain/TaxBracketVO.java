package com.example.project.gift.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxBracketVO {

    private Long bracketId;

    /** 정밀 계산용으로 bigdecimal 씀 */
    private BigDecimal taxRate;

    /** 누진 공제액 */
    private Long progressiveDeduction;
}
