package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SimulationPortfolioRecord {
    private Long portfolioId;
    private Long resultId;
    private Long simulationId;
    private ScenarioType scenarioType;
    private RiskProfile portfolioType;
    private Long depositAmount;
    private Long savingsAmount;
    private Long etfAmount;
    private Long expectedFutureValue;
    private boolean recommended;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
