package com.example.project.simulation.service;

import com.example.project.simulation.domain.GiftHistoryRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxBracket;
import com.example.project.simulation.domain.TaxPaymentMethod;
import com.example.project.simulation.dto.request.SimulationExecuteRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServiceOptimizedScenarioTest {

    private static final LocalDate GIFT_DATE = LocalDate.of(2026, 8, 12);
    private static final LocalDate SECOND_GIFT_DATE = LocalDate.of(2036, 8, 13);

    private final SimulationService service = new SimulationService(
            null,
            null,
            new SimulationCalculator(),
            null,
            null
    );

    @Test
    void noGiftHistoryCreatesSecondTrancheAfterDeductionWindow() throws Exception {
        ScenarioSnapshot snapshot = optimizedScenario(
                80_000_000L,
                50_000_000L,
                List.of(),
                240
        );

        assertEquals(2, snapshot.tranches().size());
        assertTranche(snapshot.tranches().get(0), 1, GIFT_DATE, 50_000_000L);
        assertTranche(snapshot.tranches().get(1), 2, SECOND_GIFT_DATE, 30_000_000L);
        assertTrue(snapshot.tranches().get(1).getGiftDate().isAfter(GIFT_DATE));
        assertEquals(0L, snapshot.result().getGiftTax());
    }

    @Test
    void repeatsWithinPeriodAndGiftsRemainderOnLastDeductionDate() throws Exception {
        GiftHistoryRecord previousGift = new GiftHistoryRecord();
        previousGift.setGiftId(1L);
        previousGift.setAmount(20_000_000L);
        previousGift.setGiftDate(LocalDate.of(2021, 1, 1));

        ScenarioSnapshot snapshot = optimizedScenario(
                120_000_000L,
                30_000_000L,
                List.of(previousGift),
                240
        );

        assertEquals(4, snapshot.tranches().size());
        assertTranche(snapshot.tranches().get(0), 1, GIFT_DATE, 30_000_000L);
        assertTranche(
                snapshot.tranches().get(1),
                2,
                LocalDate.of(2031, 1, 2),
                20_000_000L
        );
        assertTranche(snapshot.tranches().get(2), 3, SECOND_GIFT_DATE, 30_000_000L);
        assertTranche(
                snapshot.tranches().get(3),
                4,
                LocalDate.of(2041, 1, 3),
                40_000_000L
        );
        assertEquals(100_000_000L, snapshot.result().getDeductionAmount());
        assertEquals(20_000_000L, snapshot.result().getTaxableAmount());
        assertEquals(2_000_000L, snapshot.result().getGiftTax());
    }

    @Test
    void noFutureDeductionDateGiftsAllOnInitialDateWithoutDuplicateTranche()
            throws Exception {
        ScenarioSnapshot snapshot = optimizedScenario(
                120_000_000L,
                50_000_000L,
                List.of(),
                36
        );

        assertEquals(1, snapshot.tranches().size());
        assertTranche(snapshot.tranches().get(0), 1, GIFT_DATE, 120_000_000L);
        assertEquals(50_000_000L, snapshot.result().getDeductionAmount());
        assertEquals(70_000_000L, snapshot.result().getTaxableAmount());
        assertEquals(7_000_000L, snapshot.result().getGiftTax());
    }

    private ScenarioSnapshot optimizedScenario(
            long requestedAmount,
            long remainingDeduction,
            List<GiftHistoryRecord> completedGifts,
            int investmentPeriodMonths
    ) throws Exception {
        SimulationExecuteRequest request = new SimulationExecuteRequest();
        request.setFamilyId(1L);
        request.setRequestedAmount(requestedAmount);
        request.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        request.setInvestmentPeriodMonths(investmentPeriodMonths);
        request.setGiftDate(GIFT_DATE);

        Method optimizedScenario = SimulationService.class.getDeclaredMethod(
                "optimizedScenario",
                SimulationExecuteRequest.class,
                long.class,
                long.class,
                List.class,
                LocalDate.class,
                List.class,
                LocalDate.class
        );
        optimizedScenario.setAccessible(true);
        Object aggregate = optimizedScenario.invoke(
                service,
                request,
                remainingDeduction,
                50_000_000L,
                taxBrackets(),
                GIFT_DATE,
                completedGifts,
                GIFT_DATE.plusMonths(investmentPeriodMonths)
        );

        Method resultAccessor = aggregate.getClass().getDeclaredMethod("result");
        Method tranchesAccessor = aggregate.getClass().getDeclaredMethod("tranches");
        resultAccessor.setAccessible(true);
        tranchesAccessor.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<SimulationTrancheRecord> tranches =
                (List<SimulationTrancheRecord>) tranchesAccessor.invoke(aggregate);
        return new ScenarioSnapshot(
                (SimulationResultRecord) resultAccessor.invoke(aggregate),
                tranches
        );
    }

    private List<TaxBracket> taxBrackets() {
        TaxBracket first = new TaxBracket();
        first.setLowerBound(0L);
        first.setUpperBound(100_000_000L);
        first.setTaxRate(new BigDecimal("0.10"));
        first.setProgressiveDeduction(0L);

        TaxBracket second = new TaxBracket();
        second.setLowerBound(100_000_000L);
        second.setUpperBound(500_000_000L);
        second.setTaxRate(new BigDecimal("0.20"));
        second.setProgressiveDeduction(10_000_000L);
        return List.of(first, second);
    }

    private void assertTranche(
            SimulationTrancheRecord tranche,
            int sequence,
            LocalDate giftDate,
            long giftAmount
    ) {
        assertEquals(sequence, tranche.getSequenceNo());
        assertEquals(giftDate, tranche.getGiftDate());
        assertEquals(giftAmount, tranche.getGiftAmount());
    }

    private record ScenarioSnapshot(
            SimulationResultRecord result,
            List<SimulationTrancheRecord> tranches
    ) {
    }
}
