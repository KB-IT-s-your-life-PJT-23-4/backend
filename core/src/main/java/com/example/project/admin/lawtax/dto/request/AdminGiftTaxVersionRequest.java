package com.example.project.admin.lawtax.dto.request;

import lombok.Getter;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
public class AdminGiftTaxVersionRequest {

    @NotNull
    private LocalDate effectiveFrom;

    @NotEmpty
    @Valid
    private List<BracketItem> brackets;

    @NotEmpty
    @Valid
    private List<DeductionLimitItem> deductionLimits;

    @Getter
    public static class BracketItem {
        @NotNull
        private Long lowerBound;
        private Long upperBound;
        @NotNull
        private BigDecimal taxRate;
        @NotNull
        private Long progressiveDeduction;
    }

    @Getter
    public static class DeductionLimitItem {
        @NotNull
        private String relation;
        @NotNull
        private Boolean minor;
        @NotNull
        private Long deductionLimit;
    }
}
