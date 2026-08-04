package com.example.project.simulation.dto.response;

import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.TaxPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SimulationHistoryResponse(
        List<Item> items,
        Pagination pagination
) {
    public record Item(
            Long simulationId,
            SimulationStatus status,
            Long version,
            Family family,
            InputSummary inputSummary,
            ExpectedReturnRange expectedReturnRange,
            SelectionSummary selection,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime savedAt,
            LocalDateTime expiresAt
    ) {
    }

    public record Family(Long familyId, String recipientName, String relation) {
    }

    public record InputSummary(
            Long requestedAmount,
            TaxPaymentMethod taxPaymentMethod,
            Integer investmentPeriodMonths,
            LocalDate asOfDate,
            LocalDate investmentEndDate
    ) {
    }

    public record ExpectedReturnRange(
            String basis,
            Integer investmentPeriodMonths,
            ReturnPoint minimum,
            ReturnPoint maximum
    ) {
    }

    public record ReturnPoint(
            RiskProfile portfolioType,
            ScenarioType scenarioType,
            Long investmentPrincipal,
            Long expectedFutureValue,
            Long expectedProfit,
            BigDecimal expectedReturnRatePercent
    ) {
    }

    public record SelectionSummary(
            Long selectedPortfolioId,
            RiskProfile portfolioType,
            Long resultId,
            ScenarioType scenarioType,
            Long estimatedGiftTax,
            Long investmentPrincipal,
            Long expectedFutureValue,
            Long expectedProfit,
            BigDecimal expectedReturnRatePercent,
            List<ProductType> selectedProductTypes
    ) {
    }

    public record Pagination(
            int page,
            int size,
            long totalElements,
            int totalPages,
            int numberOfElements,
            boolean first,
            boolean last,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }
}
