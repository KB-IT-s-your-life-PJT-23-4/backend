package com.example.project.admin.lawtax.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class GiftTaxBracketRow {
    private Long bracketId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Long lowerBound;
    private Long upperBound;
    private BigDecimal taxRate;
    private Long progressiveDeduction;
    private LocalDateTime createdAt;
}
