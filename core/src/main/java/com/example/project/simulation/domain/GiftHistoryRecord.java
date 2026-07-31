package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GiftHistoryRecord {
    private Long giftId;
    private Long amount;
    private LocalDate giftDate;
}
