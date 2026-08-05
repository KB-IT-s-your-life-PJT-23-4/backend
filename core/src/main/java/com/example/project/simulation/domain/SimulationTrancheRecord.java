package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SimulationTrancheRecord {
    private Long trancheId;
    private Long resultId;
    private Integer sequenceNo;
    private LocalDate giftDate;
    private Long giftAmount;
    private Long estimatedGiftTax;
    private Long donorRequiredAmount;
    private Long investmentAmount;
    private LocalDateTime createdAt;
}
