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
import java.util.function.Function;

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
                240,
                date -> 50_000_000L
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
                240,
                date -> 50_000_000L
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
                36,
                date -> 50_000_000L
        );

        assertEquals(1, snapshot.tranches().size());
        assertTranche(snapshot.tranches().get(0), 1, GIFT_DATE, 120_000_000L);
        assertEquals(50_000_000L, snapshot.result().getDeductionAmount());
        assertEquals(70_000_000L, snapshot.result().getTaxableAmount());
        assertEquals(7_000_000L, snapshot.result().getGiftTax());
    }

    @Test
    void futureTrancheUsesDeductionLimitForRecipientAgeAtThatDate()
            throws Exception {
        ScenarioSnapshot snapshot = optimizedScenario(
                70_000_000L,
                20_000_000L,
                List.of(),
                240,
                date -> date.isBefore(LocalDate.of(2030, 1, 1))
                        ? 20_000_000L
                        : 50_000_000L
        );

        assertEquals(2, snapshot.tranches().size());
        assertTranche(snapshot.tranches().get(0), 1, GIFT_DATE, 20_000_000L);
        assertTranche(snapshot.tranches().get(1), 2, SECOND_GIFT_DATE, 50_000_000L);
        assertEquals(70_000_000L, snapshot.result().getDeductionAmount());
        assertEquals(0L, snapshot.result().getTaxableAmount());
        assertEquals(0L, snapshot.result().getGiftTax());
    }

    @Test
    void noGiftHistorySetsRenewalDateFromFirstPlannedTranche() throws Exception {
        assertEquals(
                SECOND_GIFT_DATE,
                renewalDate(List.of(), List.of(plannedTranche(GIFT_DATE)))
        );
    }

    @Test
    void renewalDateUsesAllPastAndPlannedTranches() throws Exception {
        GiftHistoryRecord expiredAtGiftDate = giftHistory(
                1L,
                10_000_000L,
                LocalDate.of(2016, 8, 11)
        );
        GiftHistoryRecord pastGift = giftHistory(
                2L,
                20_000_000L,
                LocalDate.of(2021, 1, 1)
        );

        LocalDate renewalDate = renewalDate(
                List.of(expiredAtGiftDate, pastGift),
                List.of(
                        plannedTranche(GIFT_DATE),
                        plannedTranche(LocalDate.of(2031, 1, 2))
                )
        );

        assertEquals(LocalDate.of(2031, 1, 2), renewalDate);
    }

    private ScenarioSnapshot optimizedScenario(
            long requestedAmount,
            long remainingDeduction,
            List<GiftHistoryRecord> completedGifts,
            int investmentPeriodMonths,
            Function<LocalDate, Long> deductionLimitResolver
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
                List.class,
                LocalDate.class,
                List.class,
                LocalDate.class,
                Function.class
        );
        optimizedScenario.setAccessible(true);
        Object aggregate = optimizedScenario.invoke(
                service,
                request,
                remainingDeduction,
                taxBrackets(),
                GIFT_DATE,
                completedGifts,
                GIFT_DATE.plusMonths(investmentPeriodMonths),
                deductionLimitResolver
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

    private LocalDate renewalDate(
            List<GiftHistoryRecord> gifts,
            List<SimulationTrancheRecord> plannedTranches
    ) throws Exception {
        Method method = SimulationService.class.getDeclaredMethod(
                "resolveDeductionRenewalDate",
                List.class,
                List.class,
                LocalDate.class
        );
        method.setAccessible(true);
        return (LocalDate) method.invoke(service, gifts, plannedTranches, GIFT_DATE);
    }

    private GiftHistoryRecord giftHistory(long id, long amount, LocalDate giftDate) {
        GiftHistoryRecord gift = new GiftHistoryRecord();
        gift.setGiftId(id);
        gift.setAmount(amount);
        gift.setGiftDate(giftDate);
        return gift;
    }

    private SimulationTrancheRecord plannedTranche(LocalDate giftDate) {
        SimulationTrancheRecord tranche = new SimulationTrancheRecord();
        tranche.setGiftDate(giftDate);
        return tranche;
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
