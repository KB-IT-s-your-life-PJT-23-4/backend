package com.example.project.simulation.dto.response;

import com.example.project.simulation.domain.CalculationType;
import com.example.project.simulation.domain.ProductType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductDetailResponse(
        Long simulationId,
        SimulationResponse.ProductDataVersion productDataVersion,
        Product product
) {
    public record Product(
            Long kbProductVersionId,
            Long productId,
            String productCode,
            String productName,
            ProductType productType,
            String description,
            String productUrl,
            String salesStatus,
            Object details
    ) {
    }

    public record DepositDetails(
            AmountRange subscriptionAmount,
            Term term,
            RateSummary rateSummary,
            List<BaseRateTier> baseRateTiers,
            List<PreferentialCondition> preferentialConditions,
            ReinvestmentPolicy reinvestmentPolicy,
            CalculationPolicy calculationPolicy
    ) {
    }

    public record SavingsDetails(
            String savingsCategory,
            AmountRange monthlyPayment,
            Term term,
            RateSummary rateSummary,
            List<BaseRateTier> baseRateTiers,
            List<PreferentialCondition> preferentialConditions,
            ReinvestmentPolicy reinvestmentPolicy,
            CalculationPolicy calculationPolicy
    ) {
    }

    public record EtfDetails(
            String stockCode,
            String etfCategory,
            String trackingIndex,
            BigDecimal annualizedReturn10yPercent,
            BigDecimal bondRatioPercent,
            String riskLevel,
            String returnNotice,
            List<Holding> holdings,
            BigDecimal topHoldingsWeightPercent,
            BigDecimal otherWeightPercent,
            CalculationPolicy calculationPolicy
    ) {
    }

    public record AmountRange(Long minimumAmount, Long maximumAmount) {
    }

    public record Term(Integer minimumMonths, Integer maximumMonths) {
    }

    public record RateSummary(
            BigDecimal minimumBaseRatePercent,
            BigDecimal maximumBaseRatePercent,
            BigDecimal maximumRatePercent
    ) {
    }

    public record BaseRateTier(
            Integer minimumMonths,
            Integer maximumMonths,
            BigDecimal baseRatePercent,
            BigDecimal maximumRatePercent
    ) {
    }

    public record PreferentialCondition(
            String conditionCode,
            String description,
            BigDecimal additionalRatePercent
    ) {
    }

    public record ReinvestmentPolicy(
            boolean available,
            boolean unlimited,
            String maturityValueTreatment
    ) {
    }

    public record Holding(
            Integer rank,
            String holdingName,
            String holdingCode,
            String assetType,
            String countryCode,
            BigDecimal weightPercent
    ) {
    }

    public record CalculationPolicy(
            CalculationType calculationMethod,
            String formulaVersion,
            String paymentTiming,
            String returnBasis,
            String description
    ) {
    }
}
