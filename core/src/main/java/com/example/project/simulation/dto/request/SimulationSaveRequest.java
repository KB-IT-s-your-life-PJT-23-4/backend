package com.example.project.simulation.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SimulationSaveRequest {

    @NotNull
    @Positive
    private Long version;

    @NotNull
    @Positive
    private Long selectedPortfolioId;

    @NotNull
    private Boolean replaceExistingSaved;

    @Positive
    private Long expectedExistingSavedSimulationId;

    @NotEmpty
    @Valid
    private List<ProductSelection> productSelections;

    @Valid
    private ClientCalculation clientCalculation;

    @Data
    @NoArgsConstructor
    public static class ProductSelection {
        @NotNull
        @Positive
        private Long simulationProductId;

        private List<String> preferentialConditionCodes = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class ClientCalculation {
        @NotBlank
        private String formulaVersion;

        @NotNull
        @Min(0)
        private Long expectedFutureValue;

        @NotNull
        private Long expectedProfit;
    }
}
