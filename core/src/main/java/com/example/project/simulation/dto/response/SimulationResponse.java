package com.example.project.simulation.dto.response;

import com.example.project.simulation.domain.CalculationType;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.TaxPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SimulationResponse(
        Long simulationId,
        SimulationStatus status,
        Long version,
        Family family,
        Input input,
        Long previousGiftAmount,
        Long remainingDeductionAmount,
        LocalDate deductionResetDate,
        ScenarioType recommendedScenarioType,
        ScenarioType selectedScenarioType,
        SelectedResult selectedResult,
        List<Result> results,
        FrontendCalculationPolicy frontendCalculationPolicy,
        String formulaVersion,
        String calculationVersion,
        LocalDate productDataDate,
        LocalDateTime createdAt,
        LocalDateTime savedAt,
        LocalDateTime expiresAt
) {
    public record Family(
            Long familyId,
            String recipientName,
            String relation
    ) {
    }

    public record Input(
            Long requestedAmount,
            TaxPaymentMethod taxPaymentMethod,
            Integer investmentPeriodMonths,
            LocalDate asOfDate,
            LocalDate evaluationDate
    ) {
    }

    public record Result(
            Long resultId,
            ScenarioType scenarioType,
            Long deductionAmount,
            Long taxableAmount,
            Long estimatedGiftTax,
            Long donorRequiredAmount,
            Long postTaxAmount,
            Long currentGiftAmount,
            Long deferredGiftAmount,
            Long investmentPrincipal,
            Long defaultExpectedFutureValue,
            List<Tranche> investmentTranches,
            List<Product> productRecommendations
    ) {
    }

    public record Tranche(
            Integer sequence,
            LocalDate giftDate,
            Long giftAmount,
            Long estimatedGiftTax,
            Long donorRequiredAmount,
            Long investmentAmount
    ) {
    }

    public record Product(
            Long simulationProductId,
            Long productId,
            String productName,
            ProductType productType,
            ProductType recommendationType,
            String productCategory,
            BigDecimal minAnnualRatePercent,
            BigDecimal maxAnnualRatePercent,
            BigDecimal appliedAnnualRatePercent,
            CalculationType calculationType,
            Long allocatedAmount,
            BigDecimal allocationRatio,
            Long expectedFutureValue,
            Long expectedProfit,
            Integer minMonth,
            Integer maxMonth,
            Long minAmount,
            Long maxAmount,
            Long monthlyMinAmount,
            Long monthlyMaxAmount,
            String preferentialConditions,
            String trackingIndex,
            String marketCapitalization,
            BigDecimal dividendYieldPercent,
            String riskLevel,
            String productDetailUrl,
            LocalDate productDataDate
    ) {
    }

    public record SelectedResult(
            Long resultId,
            ScenarioType scenarioType,
            RiskProfile riskProfile,
            Long estimatedGiftTax,
            Long investmentPrincipal,
            Long expectedFutureValue,
            Long expectedProfit,
            List<Product> selectedProducts
    ) {
    }

    public record FrontendCalculationPolicy(
            String formulaVersion,
            String depositCalculation,
            String savingsCalculation,
            String etfCalculation,
            String savingsPaymentTiming,
            String etfReturnBasis,
            Map<RiskProfile, Map<ProductType, BigDecimal>> portfolioAllocations
    ) {
    }
}
