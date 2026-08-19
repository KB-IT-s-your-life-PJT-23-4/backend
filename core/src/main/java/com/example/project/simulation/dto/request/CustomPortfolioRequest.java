package com.example.project.simulation.dto.request;

import com.example.project.simulation.domain.RiskProfile;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
@NoArgsConstructor
public class CustomPortfolioRequest {

    @NotNull
    @Positive
    private Long version;

    @NotNull
    @Positive
    private Long resultId;

    @NotNull
    private RiskProfile basePortfolioType;

    @NotNull
    @Valid
    private Allocation allocation;

    @Data
    @NoArgsConstructor
    public static class Allocation {

        @NotNull
        @Min(0)
        @Max(100)
        private Integer depositRatio;

        @NotNull
        @Min(0)
        @Max(100)
        private Integer savingsRatio;

        @NotNull
        @Min(0)
        @Max(100)
        private Integer etfRatio;
    }
}
