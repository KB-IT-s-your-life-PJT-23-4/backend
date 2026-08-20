package com.example.project.admin.lawtax.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class GiftDeductionLimitRow {
    private Long deductionLimitId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String relation;
    private boolean isMinor;
    private Long deductionLimit;
    private LocalDateTime createdAt;
}
