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
        GiftHistorySummary giftHistorySummary,
        ProductDataVersion productDataVersion,
        List<Recommendation> recommendations,
        Selection selection,
        List<Result> results,
        FrontendCalculationPolicy frontendCalculationPolicy,
        String calculationVersion,
        String formulaVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime savedAt,
        LocalDateTime expiresAt
) {
    public record Family(
            Long familyId,
            String recipientName,
            String relation,
            LocalDate birthDate,
            Integer ageAtSimulation,
            Boolean minorAtSimulation
    ) {
    }

    public record Input(
            Long requestedAmount,
            TaxPaymentMethod taxPaymentMethod,
            Integer investmentPeriodMonths,
            LocalDate asOfDate,
            LocalDate investmentEndDate
    ) {
    }

    public record GiftHistorySummary(
            LocalDate lookbackStartDate,
            Long previousGiftAmount,
            Long deductionLimit,
            Long usedDeductionAmount,
            Long remainingDeductionAmount,
            LocalDate deductionRenewalDate
    ) {
    }

    public record ProductDataVersion(
            Long productDataVersionId,
            String versionCode,
            LocalDate dataDate
    ) {
    }

    public record Recommendation(
            RiskProfile portfolioType,
            ScenarioType scenarioType,
            Long resultId,
            Long portfolioId
    ) {
    }

    public record Result(
            Long resultId,
            ScenarioType scenarioType,
            Long deductionAmount,
            Long taxableAmount,
            Long giftTax,
            Long donorRequiredAmount,
            Long postTaxAmount,
            Long investmentPrincipal,
            List<Tranche> tranches,
            List<Portfolio> portfolios
    ) {
    }

    public record Tranche(
            Long trancheId,
            Integer sequenceNo,
            LocalDate giftDate,
            Long giftAmount,
            Long estimatedGiftTax,
            Long donorRequiredAmount,
            Long investmentAmount
    ) {
    }

    public record Portfolio(
            Long portfolioId,
            RiskProfile portfolioType,
            Allocation allocation,
            Long expectedFutureValue,
            Long expectedProfit,
            boolean recommended,
            boolean selected,
            List<Product> productCandidates
    ) {
    }

    public record Allocation(
            Long depositAmount,
            Long savingsAmount,
            Long etfAmount
    ) {
    }

    public record Product(
            Long simulationProductId,
            Long kbProductVersionId,
            Long productId,
            String productName,
            ProductType productType,
            Long allocatedAmount,
            Long monthlyContributionAmount,
            BigDecimal allocationRatio,
            BigDecimal appliedAnnualRatePercent,
            CalculationType calculationMethod,
            ReturnMetric returnMetric,
            Long expectedFutureValue,
            Long expectedProfit,
            boolean isSelected,
            Integer minimumContractMonths,
            Integer maximumContractMonths,
            List<Reinvestment> reinvestmentSchedule,
            List<SelectedPreferentialCondition> selectedPreferentialConditions
    ) {
    }

    public record Reinvestment(
            Integer trancheSequenceNo,
            Integer renewalSequenceNo,
            LocalDate renewalDate,
            Integer completedContractMonths
    ) {
    }

    public record ReturnMetric(
            String metricType,
            BigDecimal baseRatePercent,
            BigDecimal maxRatePercent,
            BigDecimal annualReturnPercent
    ) {
    }

    public record SelectedPreferentialCondition(
            String conditionCode,
            BigDecimal additionalRatePercent
    ) {
    }

    public record Selection(
            Long selectedPortfolioId,
            RiskProfile portfolioType,
            Long resultId,
            ScenarioType scenarioType,
            Long estimatedGiftTax,
            Long donorRequiredAmount,
            Long investmentPrincipal,
            Allocation allocation,
            Long expectedFutureValue,
            Long expectedProfit,
            List<Product> selectedProducts
    ) {
    }

    public record FrontendCalculationPolicy(
            String formulaVersion,
            String rateUnit,
            String moneyRoundingMode,
            Map<ProductType, CalculationType> methods,
            String savingsPaymentTiming,
            String etfReturnBasis,
            ReinvestmentPolicy reinvestmentPolicy,
            Map<RiskProfile, Map<ProductType, BigDecimal>> portfolioAllocations
    ) {
    }

    public record ReinvestmentPolicy(
            boolean enabledForDepositAndSavings,
            boolean unlimitedReenrollment,
            String maturityValueTreatment
    ) {
    }
}
