package com.example.project.simulation.dto.response;

import com.example.project.simulation.domain.SimulationStatus;

import java.time.LocalDateTime;

public record SimulationSaveResponse(
        Long simulationId,
        SimulationStatus status,
        Long version,
        Replacement replacement,
        SimulationResponse.Selection selection,
        ServerCalculation serverCalculation,
        boolean calculationAdjusted,
        ClientServerDifference clientServerDifference,
        LocalDateTime savedAt,
        LocalDateTime updatedAt,
        LocalDateTime expiresAt
) {
    public record Replacement(
            boolean replaced,
            PreviousSimulation previousSimulation
    ) {
    }

    public record PreviousSimulation(
            Long simulationId,
            SimulationStatus previousStatus,
            SimulationStatus currentStatus,
            Long version,
            LocalDateTime expiresAt
    ) {
    }

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
