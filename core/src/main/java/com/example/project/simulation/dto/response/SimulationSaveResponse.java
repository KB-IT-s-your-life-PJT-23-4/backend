package com.example.project.simulation.dto.response;

import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationSaveResponse(
        Long simulationId,
        SimulationStatus status,
        Long version,
        ScenarioType selectedScenarioType,
        RiskProfile riskProfile,
        Long resultId,
        Long estimatedGiftTax,
        Long investmentPrincipal,
        List<SimulationResponse.Product> products,
        ServerCalculation serverCalculation,
        boolean calculationAdjusted,
        ClientServerDifference clientServerDifference,
        LocalDateTime savedAt,
        LocalDateTime expiresAt
) {
    public record ServerCalculation(
            String formulaVersion,
            Long expectedFutureValue,
            Long expectedProfit
    ) {
    }

    public record ClientServerDifference(
            Long futureValueDifference,
            Long profitDifference
    ) {
    }
}
