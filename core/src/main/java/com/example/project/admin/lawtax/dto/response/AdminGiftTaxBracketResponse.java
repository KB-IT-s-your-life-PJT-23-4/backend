package com.example.project.admin.lawtax.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AdminGiftTaxBracketResponse {
    private Long bracketId;
    private Long lowerBound;
    private Long upperBound;
    private BigDecimal taxRate;
    private Long progressiveDeduction;
}
