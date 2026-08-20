package com.example.project.batch.product.etf.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EtfPricePoint {
    private LocalDate baseDate;
    private BigDecimal closePrice;
}
