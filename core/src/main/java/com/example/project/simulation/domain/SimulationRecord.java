package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SimulationRecord {
    private Long simulationId;
    private Long productDataVersionId;
    private Long familyId;
    private Long userId;
    private String familyName;
    private String relation;
    private LocalDate birthDate;
    private String familyNameEncrypted;
    private String birthDateEncrypted;
    private Long requestedAmount;
    private SimulationStatus status;
    private TaxPaymentMethod taxPaymentMethod;
    private Integer investmentPeriodMonths;
    private LocalDate asOfDate;
    private LocalDate giftDate;
    private LocalDate investmentEndDate;
    private Long selectedPortfolioId;
    private Long previousGiftAmount;
    private Long deductionLimit;
    private LocalDate deductionRenewalDate;
    private String calculationVersion;
    private String formulaVersion;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime savedAt;
    private LocalDateTime expiredAt;
}
