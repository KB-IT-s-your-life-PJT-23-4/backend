package com.example.project.simulation.dto.request;

import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.List;

@Data
@NoArgsConstructor
public class SimulationSaveRequest {

    @NotNull
    @Positive
    private Long version;

    @NotNull
    private ScenarioType selectedScenarioType;

    @NotNull
    @Positive
    private Long resultId;

    private RiskProfile riskProfile;

    @NotEmpty
    @Valid
    private List<SelectedProduct> products;

    @Valid
    private ClientCalculation clientCalculation;

    @Data
    @NoArgsConstructor
    public static class SelectedProduct {
        @NotNull
        @Positive
        private Long productId;

        @NotNull
        private ProductType recommendationType;

        @NotNull
        @Min(1)
        private Long allocatedAmount;
    }

    @Data
    @NoArgsConstructor
    public static class ClientCalculation {
        @NotNull
        private String formulaVersion;

        @NotNull
        @Min(0)
        private Long expectedFutureValue;

        @NotNull
        private Long expectedProfit;
    }
}
