package com.example.project.admin.lawtax.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AdminGiftTaxVersionResponse {
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private boolean active;
    private boolean editable;
    private List<AdminGiftTaxBracketResponse> brackets;
    private List<AdminGiftDeductionLimitResponse> deductionLimits;
}
