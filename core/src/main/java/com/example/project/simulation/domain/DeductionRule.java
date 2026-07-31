package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DeductionRule {
    private Long deductionLimitId;
    private String relation;
    private boolean minor;
    private Long deductionLimit;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
