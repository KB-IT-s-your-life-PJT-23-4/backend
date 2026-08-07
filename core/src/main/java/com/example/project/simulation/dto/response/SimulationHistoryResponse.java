package com.example.project.simulation.dto.response;

import com.example.project.common.api.Pagination;
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
            LocalDate giftDate,
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
            List<ProductType> selectedProductTypes,
            List<SelectedProductSummary> selectedProducts
    ) {
    }

    public static final class SelectedProductSummary {

        private final String productName;
        private final ProductType productType;
        private final Long allocatedAmount;
        private final BigDecimal appliedAnnualRatePercent;
        private final Long expectedFutureValue;

        public SelectedProductSummary(
                String productName,
                ProductType productType,
                Long allocatedAmount,
                BigDecimal appliedAnnualRatePercent,
                Long expectedFutureValue
        ) {
            this.productName = productName;
            this.productType = productType;
            this.allocatedAmount = allocatedAmount;
            this.appliedAnnualRatePercent = appliedAnnualRatePercent;
            this.expectedFutureValue = expectedFutureValue;
        }

        public String getProductName() {
            return productName;
        }

        public ProductType getProductType() {
            return productType;
        }

        public Long getAllocatedAmount() {
            return allocatedAmount;
        }

        public BigDecimal getAppliedAnnualRatePercent() {
            return appliedAnnualRatePercent;
        }

        public Long getExpectedFutureValue() {
            return expectedFutureValue;
        }
    }

}
