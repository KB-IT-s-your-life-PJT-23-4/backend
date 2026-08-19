package com.example.project.batch.product.etf.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class ExternalEtfPrice {
    private String stockCode;
    private LocalDate baseDate;
    private BigDecimal closePrice;
    private String dataSource;
}
