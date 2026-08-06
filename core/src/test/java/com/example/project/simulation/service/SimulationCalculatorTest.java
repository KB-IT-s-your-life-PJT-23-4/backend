package com.example.project.simulation.service;

import com.example.project.simulation.domain.CalculationType;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.TaxBracket;
import com.example.project.simulation.domain.TaxPaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationCalculatorTest {

    private final SimulationCalculator calculator = new SimulationCalculator();

    @Test
    @DisplayName("예금은 전체 원금에 운용 기간만큼 단리를 적용한다")
    void calculateDepositSimpleInterest() {
        long result = calculator.calculateProductFutureValue(
                CalculationType.SIMPLE_INTEREST,
                38_568_000L,
                new BigDecimal("3.4"),
                36
        );

        assertEquals(42_501_936L, result);
    }

    @Test
    @DisplayName("적금은 총 배분액을 월말 적립식으로 계산한다")
    void calculateSavingsMonthlyInstallment() {
        long result = calculator.calculateProductFutureValue(
                CalculationType.MONTHLY_INSTALLMENT,
                36_000_000L,
                new BigDecimal("3.7"),
                36
        );

        assertEquals(38_012_140L, result);
    }

    @Test
    @DisplayName("예금은 만기 원리금을 동일 상품에 재가입해 전체 운용 기간을 채운다")
    void calculateDepositWithUnlimitedReinvestment() {
        long result = calculator.calculateReinvestedProductFutureValue(
                CalculationType.SIMPLE_INTEREST,
                100_000_000L,
                new BigDecimal("3.4"),
                36,
                12,
                12
        );

        assertEquals(110_550_730L, result);
        assertEquals(List.of(12, 12, 12),
                calculator.reinvestmentPeriods(36, 12, 12));
    }

    @Test
    @DisplayName("가입 기간을 조합해 운용 기간을 정확히 채울 수 있다")
    void distributeContractMonthsAcrossReenrollments() {
        assertEquals(
                List.of(12, 12, 11),
                calculator.reinvestmentPeriods(35, 6, 12)
        );
    }

    @Test
    @DisplayName("재가입을 반복해도 운용 기간을 정확히 채울 수 없으면 제외한다")
    void rejectProductWhenContractTermsCannotCoverInvestmentPeriod() {
        assertTrue(calculator.reinvestmentPeriods(35, 12, 12).isEmpty());
    }

    @Test
    @DisplayName("표시 금리가 높아도 월 적립식 실수익이 예금 단리보다 낮을 수 있다")
    void compareEffectiveReturnsUsingProductSpecificFormula() {
        long depositValue = calculator.calculateProductFutureValue(
                CalculationType.SIMPLE_INTEREST,
                100_000_000L,
                new BigDecimal("3.2"),
                36
        );
        long savingsValue = calculator.calculateProductFutureValue(
                CalculationType.MONTHLY_INSTALLMENT,
                100_000_000L,
                new BigDecimal("4.0"),
                36
        );

        assertTrue(depositValue > savingsValue);
    }

    @Test
    @DisplayName("ETF는 최근 5년 연평균 수익률을 연복리로 계산한다")
    void calculateEtfCompoundReturn() {
        long result = calculator.calculateProductFutureValue(
                CalculationType.COMPOUND_RETURN,
                18_642_000L,
                new BigDecimal("5.1"),
                36
        );

        assertEquals(21_642_162L, result);
    }

    @Test
    @DisplayName("수증자가 세금을 내면 증여세를 투자 원금에서 차감한다")
    void recipientPaysGiftTax() {
        SimulationCalculator.TaxOutcome result = calculator.calculateTax(
                100_000_000L,
                30_000_000L,
                TaxPaymentMethod.RECIPIENT_PAYS,
                brackets()
        );

        assertEquals(70_000_000L, result.taxableAmount());
        assertEquals(7_000_000L, result.giftTax());
        assertEquals(93_000_000L, result.investmentAmount());
        assertEquals(100_000_000L, result.donorRequiredAmount());
    }

    @Test
    @DisplayName("증여자가 세금을 대납하면 수증자의 투자 원금은 증여액 전액이다")
    void donorPaysGiftTaxWithoutReducingRecipientPrincipal() {
        SimulationCalculator.TaxOutcome result = calculator.calculateTax(
                100_000_000L,
                30_000_000L,
                TaxPaymentMethod.DONOR_PAYS,
                brackets()
        );

        assertEquals(100_000_000L, result.investmentAmount());
        assertEquals(
                100_000_000L + result.giftTax(),
                result.donorRequiredAmount()
        );
    }

    @Test
    @DisplayName("단기 균형형은 안전자산 80%와 ETF 20%를 적용한다")
    void balancedPortfolioTotalsOneHundred() {
        Map<ProductType, BigDecimal> balanced =
                PortfolioPolicy.allocations(36).get(RiskProfile.BALANCED);

        BigDecimal total = balanced.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("100"), total);
        assertEquals(BigDecimal.ZERO, balanced.get(ProductType.DEPOSIT));
        assertEquals(new BigDecimal("80"), balanced.get(ProductType.SAVINGS));
        assertEquals(new BigDecimal("20"), balanced.get(ProductType.ETF));
    }

    @Test
    @DisplayName("적금 실수익률이 더 높으면 납입 한도까지 적금에 배분한다")
    void allocateSavingsWhenEffectiveReturnIsHigher() {
        Map<ProductType, BigDecimal> balanced =
                PortfolioPolicy.allocations(36).get(RiskProfile.BALANCED);

        Map<ProductType, Long> allocation = PortfolioPolicy.allocateByEffectiveReturn(
                100_000_000L,
                balanced,
                18_000_000L,
                103_000_000L,
                106_000_000L
        );

        assertEquals(62_000_000L, allocation.get(ProductType.DEPOSIT));
        assertEquals(18_000_000L, allocation.get(ProductType.SAVINGS));
        assertEquals(20_000_000L, allocation.get(ProductType.ETF));
    }

    @Test
    @DisplayName("예금 실수익률이 같거나 더 높으면 안전자산 전액을 예금에 배분한다")
    void allocateSafeAssetsToDepositWhenEffectiveReturnIsNotLower() {
        Map<ProductType, BigDecimal> balanced =
                PortfolioPolicy.allocations(36).get(RiskProfile.BALANCED);

        Map<ProductType, Long> allocation = PortfolioPolicy.allocateByEffectiveReturn(
                100_000_000L,
                balanced,
                100_000_000L,
                110_000_000L,
                106_000_000L
        );

        assertEquals(80_000_000L, allocation.get(ProductType.DEPOSIT));
        assertEquals(0L, allocation.get(ProductType.SAVINGS));
        assertEquals(20_000_000L, allocation.get(ProductType.ETF));
    }

    private List<TaxBracket> brackets() {
        return List.of(
                bracket(0L, 100_000_000L, "0.10", 0L),
                bracket(100_000_001L, 500_000_000L, "0.20", 10_000_000L)
        );
    }

    private TaxBracket bracket(
            Long lower,
            Long upper,
            String rate,
            Long deduction
    ) {
        TaxBracket bracket = new TaxBracket();
        bracket.setLowerBound(lower);
        bracket.setUpperBound(upper);
        bracket.setTaxRate(new BigDecimal(rate));
        bracket.setProgressiveDeduction(deduction);
        return bracket;
    }
}
