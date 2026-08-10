package com.example.project.simulation.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EtfPriceRecord {
    private Long productId;
    private LocalDate baseDate;
    private BigDecimal closePrice;
}
