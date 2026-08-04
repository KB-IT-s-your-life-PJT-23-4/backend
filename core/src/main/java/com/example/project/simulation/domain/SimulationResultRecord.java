package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SimulationResultRecord {
    private Long resultId;
    private Long simulationId;
    private ScenarioType scenarioType;
    private Long deductionAmount;
    private Long taxableAmount;
    private Long giftTax;
    private Long donorRequiredAmount;
    private Long postTaxAmount;
    private Long investmentPrincipal;
    private LocalDateTime createdAt;
}
