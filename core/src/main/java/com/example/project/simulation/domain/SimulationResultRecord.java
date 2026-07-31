package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SimulationResultRecord {
    private Long resultId;
    private Long simulationId;
    private ScenarioType scenarioType;
    private RiskProfile riskProfile;
    private Long deductionAmount;
    private Long taxableAmount;
    private Long giftTax;
    private Long donorRequiredAmount;
    private Long postTaxAmount;
    private Long currentAmount;
    private Long deferredAmount;
    private Long investmentPrincipal;
    private Long expectedFutureValue;
    private LocalDateTime createdAt;
}
