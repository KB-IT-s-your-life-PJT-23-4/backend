package com.example.project.simulation.dto.request;

import com.example.project.simulation.domain.TaxPaymentMethod;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
@Data
@NoArgsConstructor
public class SimulationExecuteRequest {

    @NotNull
    @Positive
    private Long familyId;

    @NotNull
    @Min(1)
    private Long requestedAmount;

    @NotNull
    private TaxPaymentMethod taxPaymentMethod;

    @NotNull
    @Min(1)
    @Max(240)
    private Integer investmentPeriodMonths;
}
