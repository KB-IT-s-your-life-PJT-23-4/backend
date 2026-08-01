package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxBracket {
    private Long bracketId;
    private Long lowerBound;
    private Long upperBound;
    private BigDecimal taxRate;
    private Long progressiveDeduction;
}
