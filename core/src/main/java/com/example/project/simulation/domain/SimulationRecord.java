package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SimulationRecord {
    private Long simulationId;
    private Long familyId;
    private Long userId;
    private String familyName;
    private String relation;
    private LocalDate birthDate;
    private Long requestedAmount;
    private SimulationStatus status;
    private TaxPaymentMethod taxPaymentMethod;
    private Integer investmentPeriodMonths;
    private LocalDate asOfDate;
    private LocalDate investmentEndDate;
    private ScenarioType recommendedScenarioType;
    private ScenarioType selectedScenarioType;
    private Long selectedResultId;
    private RiskProfile selectedRiskProfile;
    private Long previousGiftAmount;
    private Long remainingDeductionAmount;
    private LocalDate deductionResetDate;
    private String calculationVersion;
    private String formulaVersion;
    private LocalDate productDataDate;
    private Long version;
    private LocalDateTime savedAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
}
