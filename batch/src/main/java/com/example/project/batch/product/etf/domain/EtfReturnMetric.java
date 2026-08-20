package com.example.project.batch.product.etf.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class EtfReturnMetric {
    private Long productId;
    private BigDecimal annualReturn10yPercent;
    private LocalDate startDate;
    private LocalDate endDate;
}
