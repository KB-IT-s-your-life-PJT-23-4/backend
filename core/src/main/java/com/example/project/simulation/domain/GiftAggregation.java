package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GiftAggregation {
    private Long completedAmount;
    private LocalDate oldestGiftDate;
}
