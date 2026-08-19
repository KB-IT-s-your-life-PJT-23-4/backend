package com.example.project.simulation.service;

import com.example.project.simulation.domain.DeductionRule;
import com.example.project.simulation.domain.BaseRateRecord;
import com.example.project.simulation.domain.FamilySnapshot;
import com.example.project.simulation.domain.GiftHistoryRecord;
import com.example.project.simulation.domain.ProductCandidate;
import com.example.project.simulation.domain.ProductDataVersionRecord;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxBracket;
import com.example.project.simulation.domain.TaxPaymentMethod;
import com.example.project.simulation.dto.request.SimulationExecuteRequest;
import com.example.project.simulation.dto.request.CustomPortfolioRequest;
import com.example.project.simulation.dto.response.CustomPortfolioResponse;
import com.example.project.simulation.dto.response.SimulationResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.mapper.SimulationMapper;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServiceExecuteTest {

    @Test
    @DisplayName("추천 포트폴리오를 기준으로 커스텀 비율을 생성하고 다시 요청하면 교체한다")
    void createAndReplaceCustomPortfolio() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        SimulationResponse initial = fixture.execute(100_000_000L, 120, giftDate);
        SimulationResponse.Recommendation base = initial.recommendations().stream()
                .filter(item -> item.portfolioType() == RiskProfile.BALANCED)
                .findFirst()
                .orElseThrow();

        CustomPortfolioResponse first = fixture.service().customizePortfolio(
                initial.simulationId(),
                customRequest(initial.version(), base.resultId(), 30, 30, 40),
                USER_ID
        );
        SimulationResponse.Portfolio firstCustom = customPortfolio(first.getSimulation());
        long principal = first.getSimulation().results().stream()
                .filter(item -> Objects.equals(item.resultId(), base.resultId()))
                .findFirst()
                .orElseThrow()
                .investmentPrincipal();

        // 회귀 방지: 사용자가 정한 세 유형의 금액 합은 투자 원금과 정확히 일치해야 한다.
        assertEquals(Math.round(principal * 0.30), firstCustom.allocation().depositAmount());
        assertEquals(Math.round(principal * 0.30), firstCustom.allocation().savingsAmount());
        assertEquals(
                principal - firstCustom.allocation().depositAmount()
                        - firstCustom.allocation().savingsAmount(),
                firstCustom.allocation().etfAmount()
        );
        assertEquals(2L, first.getSimulation().version());

        CustomPortfolioResponse replaced = fixture.service().customizePortfolio(
                initial.simulationId(),
                customRequest(2L, base.resultId(), 40, 20, 40),
                USER_ID
        );
        SimulationResponse.Portfolio replacedCustom = customPortfolio(replaced.getSimulation());

        // 회귀 방지: CUSTOM은 누적하지 않고 시뮬레이션당 최신 한 건으로 교체한다.
        assertEquals(1, fixture.portfolios.stream()
                .filter(item -> item.getPortfolioType() == RiskProfile.CUSTOM)
                .count());
        assertEquals(Math.round(principal * 0.40), replacedCustom.allocation().depositAmount());
        assertEquals(Math.round(principal * 0.20), replacedCustom.allocation().savingsAmount());
        assertEquals(3L, replaced.getSimulation().version());
    }

    private static final long USER_ID = 7L;
    private static final long FAMILY_ID = 31L;
    private static final long MINOR_DEDUCTION_LIMIT = 20_000_000L;
    private static final long ADULT_DEDUCTION_LIMIT = 50_000_000L;

    @Test
    @DisplayName("과거 증여가 없고 공제 한도를 초과하면 갱신일에 잔액을 분할한다")
    void splitOverLimitWithoutGiftHistory() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);

        SimulationResponse response = fixture.execute(80_000_000L, 240, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 과거 이력이 없을 때 두 번째 회차가 첫 회차와 같은 날 생성되면 안 된다.
        assertEquals(List.of(50_000_000L, 30_000_000L), giftAmounts(optimized));
        assertEquals(giftDate, optimized.tranches().get(0).giftDate());
        assertEquals(giftDate.plusYears(10).plusDays(1),
                optimized.tranches().get(1).giftDate());
        assertEquals(0L, optimized.giftTax());
    }

    @Test
    @DisplayName("11년 운용의 분할 회차는 공통 평가일을 하루 연장해 12개월 계약을 구성한다")
    void extendEvaluationDateForElevenYearOptimizedSplit() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        fixture.useSafeAssetTerms(12, 36);

        SimulationResponse response = fixture.execute(80_000_000L, 132, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 10년+1일 회차가 11개월로 절삭되어 상품 후보가 모두 탈락하면 안 된다.
        assertEquals(response.input().investmentEndDate().plusDays(1),
                response.input().evaluationDate());
        assertEquals(2, optimized.tranches().size());
        assertTrue(optimized.portfolios().stream()
                .flatMap(portfolio -> portfolio.productCandidates().stream())
                .filter(product -> product.productType() != ProductType.ETF)
                .anyMatch(product -> product.contractRateSchedule().stream()
                        .anyMatch(contract -> contract.trancheSequenceNo() == 2
                                && contract.contractMonths() == 12)));

        // 시나리오 비교가 서로 다른 날짜를 사용하지 않도록 즉시 증여도 같은 평가일을 사용한다.
        SimulationResponse.Result immediate = result(response, ScenarioType.IMMEDIATE);
        assertTrue(immediate.portfolios().stream()
                .allMatch(portfolio -> portfolio.expectedFutureValue() > 0));
    }

    @Test
    @DisplayName("평가일은 10년 초과 운용과 분할 회차 조건을 모두 만족할 때만 연장한다")
    void extendEvaluationDateOnlyForLongTermSplitScenario() {
        LocalDate giftDate = futureDate();

        SimulationResponse tenYearResponse = adultFixture(giftDate)
                .execute(80_000_000L, 120, giftDate);
        SimulationResponse noSplitResponse = adultFixture(giftDate)
                .execute(40_000_000L, 132, giftDate);

        // 회귀 방지: 정확히 10년이거나 10년을 초과해도 분할 회차가 없으면 날짜를 보정하지 않는다.
        assertEquals(tenYearResponse.input().investmentEndDate(),
                tenYearResponse.input().evaluationDate());
        assertEquals(noSplitResponse.input().investmentEndDate(),
                noSplitResponse.input().evaluationDate());
        assertEquals(1, result(noSplitResponse, ScenarioType.TAX_OPTIMIZED)
                .tranches().size());
    }

    @Test
    @DisplayName("상품 계약으로 채울 수 없는 잔여기간은 원금 대기 일정으로 반환한다")
    void returnCashHoldingForUncoveredContractRemainder() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        fixture.useSafeAssetTerms(12, 12);

        SimulationResponse response = fixture.execute(40_000_000L, 37, giftDate);
        SimulationResponse.Product deposit = result(response, ScenarioType.IMMEDIATE)
                .portfolios().get(0).productCandidates().stream()
                .filter(product -> product.productType() == ProductType.DEPOSIT)
                .findFirst()
                .orElseThrow();

        // 회귀 방지: 12개월 계약 3회 뒤 남은 1개월 때문에 실행 전체가 실패하면 안 된다.
        assertEquals(List.of(12, 12, 12), deposit.contractRateSchedule().stream()
                .map(SimulationResponse.ContractRate::contractMonths)
                .toList());
        assertEquals(1, deposit.cashHoldingSchedule().size());
        assertEquals(1, deposit.cashHoldingSchedule().get(0).holdingMonths());
        assertTrue(deposit.cashHoldingSchedule().get(0).holdingAmount()
                >= deposit.allocatedAmount());
    }

    @Test
    @DisplayName("적금 한도는 재가입 전체 기간이 아니라 첫 계약 납입기간으로 계산한다")
    void capSavingsAllocationByFirstContractContributionMonths() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        fixture.useSafeAssetTerms(12, 36);
        fixture.depositBaseRate = new BigDecimal("2.00");
        fixture.savingsBaseRate = new BigDecimal("5.00");
        fixture.savingsCandidates.get(0).setMonthlyMinAmount(10_000L);
        fixture.savingsCandidates.get(0).setMonthlyMaxAmount(1_000_000L);

        SimulationResponse response = fixture.execute(
                100_000_000L,
                60,
                giftDate
        );
        SimulationResponse.Portfolio portfolio = result(
                response,
                ScenarioType.IMMEDIATE
        ).portfolios().stream()
                .filter(item -> item.portfolioType() == RiskProfile.CONSERVATIVE)
                .findFirst()
                .orElseThrow();
        SimulationResponse.Product savings = portfolio.productCandidates().stream()
                .filter(product -> product.productType() == ProductType.SAVINGS)
                .findFirst()
                .orElseThrow();

        // 회귀 방지: 60개월을 36+24개월로 재가입해도 신규 원금은 첫 36개월에 납입한다.
        assertEquals(36_000_000L, portfolio.allocation().savingsAmount());
        assertEquals(1_000_000L, savings.monthlyContributionAmount());
        assertTrue(portfolio.allocation().depositAmount() > 0);
    }

    @Test
    @DisplayName("짧은 마지막 분할 회차가 있어도 정상 상품 후보를 유지한다")
    void keepCandidateWhenOnlyLastTrancheIsTooShort() {
        LocalDate giftDate = futureDate();
        LocalDate birthDate = giftDate.minusYears(19).plusMonths(11);
        Fixture fixture = new Fixture(birthDate);
        fixture.useSafeAssetTerms(12, 36);

        SimulationResponse response = fixture.execute(50_000_000L, 12, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);
        SimulationResponse.Product deposit = optimized.portfolios().get(0)
                .productCandidates().stream()
                .filter(product -> product.productType() == ProductType.DEPOSIT)
                .findFirst()
                .orElseThrow();

        // 회귀 방지: 첫 회차는 가입 가능하고 마지막 회차만 짧다면 상품 전체를 제외하지 않는다.
        assertEquals(2, optimized.tranches().size());
        assertEquals(giftDate.plusMonths(11), optimized.tranches().get(1).giftDate());
        assertTrue(deposit.contractRateSchedule().stream()
                .anyMatch(contract -> contract.trancheSequenceNo() == 1));
        assertTrue(deposit.cashHoldingSchedule().stream()
                .anyMatch(holding -> holding.trancheSequenceNo() == 2));

        // 회귀 방지: 대기 자금 반영 후에도 각 성향의 추천은 종료 시점 총 가치로 결정한다.
        for (RiskProfile profile : RiskProfile.presetValues()) {
            SimulationResponse.Recommendation recommendation = response.recommendations().stream()
                    .filter(item -> item.portfolioType() == profile)
                    .findFirst()
                    .orElseThrow();
            SimulationResponse.Result expected = response.results().stream()
                    .max(Comparator.<SimulationResponse.Result>comparingLong(
                                    item -> scenarioEndValue(item, profile))
                            .thenComparingLong(item -> -item.giftTax()))
                    .orElseThrow();

            assertEquals(expected.scenarioType(), recommendation.scenarioType());
            assertEquals(expected.resultId(), recommendation.resultId());
        }
    }

    @Test
    @DisplayName("모든 회차가 최소 가입기간보다 짧으면 해당 예적금 후보를 사용할 수 없다")
    void rejectCandidateWhenEveryTrancheIsTooShort() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        fixture.useSafeAssetTerms(13, 36);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.execute(40_000_000L, 12, giftDate)
        );

        // 회귀 방지: 수익을 낼 수 있는 회차가 하나도 없는데 대기 자금만으로 추천하지 않는다.
        assertEquals(SimulationError.PRODUCT_CANDIDATE_NOT_FOUND, exception.getError());
        assertEquals(0, fixture.insertSimulationCount);
    }

    @Test
    @DisplayName("과거 증여가 있고 남은 공제가 있으면 과거·계획 회차의 해제일을 모두 반영한다")
    void splitWithGiftHistoryAndRemainingDeduction() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        LocalDate priorGiftDate = giftDate.minusYears(5);
        fixture.addCompletedGift(20_000_000L, priorGiftDate);

        SimulationResponse response = fixture.execute(80_000_000L, 240, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 최초 남은 공제, 과거 증여 해제, 계획 증여 해제를 순서대로 사용한다.
        assertEquals(List.of(30_000_000L, 20_000_000L, 30_000_000L),
                giftAmounts(optimized));
        assertEquals(List.of(
                        giftDate,
                        priorGiftDate.plusYears(10).plusDays(1),
                        giftDate.plusYears(10).plusDays(1)
                ),
                giftDates(optimized));
        assertEquals(0L, optimized.giftTax());
    }

    @Test
    @DisplayName("남은 공제가 0원이면 다음 공제 가능일까지 첫 회차를 미룬다")
    void deferFirstTrancheWhenRemainingDeductionIsZero() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        LocalDate priorGiftDate = giftDate.minusYears(5);
        fixture.addCompletedGift(ADULT_DEDUCTION_LIMIT, priorGiftDate);

        SimulationResponse response = fixture.execute(40_000_000L, 120, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 공제 여력이 0인데 0원 회차나 과세 회차를 최초 일자에 만들지 않는다.
        assertEquals(1, optimized.tranches().size());
        assertEquals(priorGiftDate.plusYears(10).plusDays(1),
                optimized.tranches().get(0).giftDate());
        assertEquals(40_000_000L, optimized.tranches().get(0).giftAmount());
        assertEquals(0L, optimized.giftTax());
    }

    @Test
    @DisplayName("남은 공제가 0원이어도 서로 다른 공제 해제일의 일부 공제를 순서대로 사용한다")
    void usePartiallyReleasedDeductionsWhenCurrentRemainingDeductionIsZero() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        LocalDate firstPriorGiftDate = giftDate.minusYears(9);
        LocalDate secondPriorGiftDate = giftDate.minusYears(6);
        fixture.addCompletedGift(20_000_000L, firstPriorGiftDate);
        fixture.addCompletedGift(30_000_000L, secondPriorGiftDate);

        SimulationResponse response = fixture.execute(50_000_000L, 60, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 최초 남은 공제가 0원이더라도 첫 해제일에 생긴 2천만 원의 공제를 건너뛰지 않는다.
        assertEquals(List.of(20_000_000L, 30_000_000L), giftAmounts(optimized));
        assertEquals(List.of(
                        firstPriorGiftDate.plusYears(10).plusDays(1),
                        secondPriorGiftDate.plusYears(10).plusDays(1)
                ),
                giftDates(optimized));
        assertEquals(0L, optimized.giftTax());
    }

    @Test
    @DisplayName("분할 증여의 첫 회차와 다음 회차는 서로 다른 날짜를 가진다")
    void firstAndNextTrancheDatesAreDifferent() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);

        SimulationResponse response = fixture.execute(60_000_000L, 240, giftDate);
        List<SimulationResponse.Tranche> tranches =
                result(response, ScenarioType.TAX_OPTIMIZED).tranches();

        // 회귀 방지: 공제 해제일 계산 오류로 동일 날짜 회차가 중복 생성되는 것을 막는다.
        assertEquals(2, tranches.size());
        assertNotEquals(tranches.get(0).giftDate(), tranches.get(1).giftDate());
        assertTrue(tranches.get(1).giftDate().isAfter(tranches.get(0).giftDate()));
    }

    @Test
    @DisplayName("미성년 수증자가 다음 회차에 성년이면 성년 공제 한도를 적용한다")
    void applyAdultDeductionWhenMinorBecomesAdult() {
        LocalDate giftDate = futureDate();
        Fixture fixture = new Fixture(giftDate.minusYears(10));

        SimulationResponse response = fixture.execute(70_000_000L, 240, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 최초 회차의 미성년 공제 한도를 미래 회차에 고정 적용하지 않는다.
        assertEquals(List.of(20_000_000L, 30_000_000L, 20_000_000L),
                giftAmounts(optimized));
        assertEquals(70_000_000L, optimized.deductionAmount());
        assertEquals(0L, optimized.giftTax());
        assertTrue(fixture.deductionQueries.stream().anyMatch(Query::minor));
        assertTrue(fixture.deductionQueries.stream().anyMatch(query -> !query.minor()));
    }

    @Test
    @DisplayName("18세 자녀에게 5천만 원을 증여하면 성년 전후로 공제 한도만큼 분할한다")
    void splitGiftAtAdulthoodForEighteenYearOldChild() {
        LocalDate giftDate = futureDate();
        LocalDate adulthoodDate = giftDate.plusYears(1);
        Fixture fixture = new Fixture(giftDate.minusYears(18));

        SimulationResponse response = fixture.execute(50_000_000L, 24, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        assertEquals(List.of(20_000_000L, 30_000_000L), giftAmounts(optimized));
        assertEquals(List.of(giftDate, adulthoodDate), giftDates(optimized));
        assertEquals(50_000_000L, optimized.deductionAmount());
        assertEquals(0L, optimized.taxableAmount());
        assertEquals(0L, optimized.giftTax());
    }

    @Test
    @DisplayName("공제 갱신 후에도 잔액이 한도를 넘으면 마지막 공제 가능일에 초과분 세금을 계산한다")
    void taxRemainderThatStillExceedsRenewedDeduction() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);

        SimulationResponse response = fixture.execute(120_000_000L, 240, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 입력 기간 안의 마지막 공제 가능일에는 잔액 전부를 증여하고 초과분을 과세한다.
        assertEquals(List.of(50_000_000L, 70_000_000L), giftAmounts(optimized));
        assertEquals(100_000_000L, optimized.deductionAmount());
        assertEquals(20_000_000L, optimized.taxableAmount());
        assertEquals(1_940_000L, optimized.giftTax());
    }

    @Test
    @DisplayName("증여 예정일과 같은 날 완료된 기존 증여도 최근 10년 합산에 포함한다")
    void includeCompletedGiftOnSameDay() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        fixture.addCompletedGift(20_000_000L, giftDate);

        SimulationResponse response = fixture.execute(40_000_000L, 36, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 조회 종료일을 배타적으로 처리해 같은 날 증여를 누락하지 않는다.
        assertEquals(20_000_000L, response.giftHistorySummary().previousGiftAmount());
        assertEquals(30_000_000L, response.giftHistorySummary().remainingDeductionAmount());
        assertEquals(10_000_000L, optimized.taxableAmount());
        assertEquals(970_000L, optimized.giftTax());
        assertEquals(giftDate, fixture.lastCompletedGiftQueryEnd);
    }

    @Test
    @DisplayName("다음 공제 가능일이 운용 종료일 이후면 종료 전 마지막 회차에 잔액을 모두 반영한다")
    void doNotCreateTrancheAfterInvestmentEndDate() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);

        SimulationResponse response = fixture.execute(80_000_000L, 120, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 회귀 방지: 운용 종료일을 넘긴 회차를 만들지 않고 현재 회차에서 잔액과 세금을 확정한다.
        assertEquals(1, optimized.tranches().size());
        assertTrue(optimized.tranches().stream()
                .noneMatch(tranche -> tranche.giftDate().isAfter(response.input().investmentEndDate())));
        assertEquals(80_000_000L, optimized.tranches().get(0).giftAmount());
        assertEquals(30_000_000L, optimized.taxableAmount());
        assertEquals(2_910_000L, optimized.giftTax());
    }

    @Test
    @DisplayName("추천 시나리오는 동일 성향에서 종료 시점 총 가치가 큰 쪽을 선택한다")
    void recommendScenarioWithGreaterFutureValue() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);

        SimulationResponse response = fixture.execute(80_000_000L, 240, giftDate);

        // 회귀 방지: 절세 여부만으로 고정 추천하지 않고 운용 종료 시점의 총 가치를 비교한다.
        for (RiskProfile profile : RiskProfile.presetValues()) {
            SimulationResponse.Recommendation recommendation = response.recommendations().stream()
                    .filter(item -> item.portfolioType() == profile)
                    .findFirst()
                    .orElseThrow();
            SimulationResponse.Result expected = response.results().stream()
                    .max(Comparator.<SimulationResponse.Result>comparingLong(
                                    item -> scenarioEndValue(item, profile))
                            .thenComparingLong(item -> -item.giftTax()))
                    .orElseThrow();

            assertEquals(expected.scenarioType(), recommendation.scenarioType());
            assertEquals(expected.resultId(), recommendation.resultId());
        }
    }

    @Test
    @DisplayName("증여액이 공제 한도와 같으면 불필요한 추가 회차를 만들지 않는다")
    void doNotCreateRedundantTrancheAtExactDeductionLimit() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);

        SimulationResponse response = fixture.execute(ADULT_DEDUCTION_LIMIT, 240, giftDate);
        SimulationResponse.Result optimized = result(response, ScenarioType.TAX_OPTIMIZED);

        // 추가 경계값 검증: 잔액이 0원이 된 뒤 공제 갱신 회차를 더 생성하지 않는다.
        assertEquals(1, optimized.tranches().size());
        assertEquals(ADULT_DEDUCTION_LIMIT, optimized.tranches().get(0).giftAmount());
        assertEquals(0L, optimized.giftTax());
    }

    @Test
    @DisplayName("동일 멱등성 키와 동일 실행 요청은 최초 응답을 재사용한다")
    void reuseIdempotentExecuteResponse() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        SimulationService service = fixture.service();
        SimulationExecuteRequest request = request(80_000_000L, 240, giftDate);

        SimulationResponse first = service.execute(request, USER_ID, "execute-key");
        SimulationResponse second = service.execute(request, USER_ID, "execute-key");

        // 회귀 방지: 네트워크 재시도가 동일 시뮬레이션을 두 번 생성하지 않도록 한다.
        assertSame(first, second);
        assertEquals(1, fixture.insertSimulationCount);
        assertEquals(2, fixture.results.size());
    }

    @Test
    @DisplayName("같은 멱등성 키에 다른 실행 요청이 오면 충돌로 처리한다")
    void rejectDifferentExecuteRequestWithSameIdempotencyKey() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        SimulationService service = fixture.service();
        service.execute(request(80_000_000L, 240, giftDate), USER_ID, "execute-key");

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> service.execute(
                        request(90_000_000L, 240, giftDate),
                        USER_ID,
                        "execute-key"
                )
        );

        // 회귀 방지: 같은 키를 다른 금액에 재사용해 최초 요청 의미가 변조되는 것을 막는다.
        assertEquals(SimulationError.IDEMPOTENCY_KEY_CONFLICT, exception.getError());
        assertEquals(1, fixture.insertSimulationCount);
    }

    @Test
    @DisplayName("주는 분이 세금을 준비하면 운용 원금은 유지되고 총 준비 금액이 증가한다")
    void calculateDonorPaysScenario() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        SimulationExecuteRequest request = request(80_000_000L, 36, giftDate);
        request.setTaxPaymentMethod(TaxPaymentMethod.DONOR_PAYS);

        SimulationResponse response = fixture.service().execute(request, USER_ID, null);
        SimulationResponse.Result immediate = result(response, ScenarioType.IMMEDIATE);

        // 회귀 방지: 세금 대납 시 세금을 증여액에서 차감하는 수증자 납부 계산을 적용하지 않는다.
        assertEquals(80_000_000L, immediate.investmentPrincipal());
        assertEquals(80_000_000L, immediate.postTaxAmount());
        assertTrue(immediate.giftTax() > 0);
        assertEquals(80_000_000L + immediate.giftTax(), immediate.donorRequiredAmount());
    }

    @Test
    @DisplayName("필수 기준 데이터가 없으면 불완전한 결과를 저장하지 않고 명시적으로 실패한다")
    void rejectExecutionWhenReferenceDataIsMissing() {
        LocalDate giftDate = futureDate();

        Fixture missingDeduction = adultFixture(giftDate);
        missingDeduction.deductionRuleAvailable = false;
        assertExecutionError(
                missingDeduction,
                giftDate,
                SimulationError.DEDUCTION_RULE_NOT_FOUND
        );

        Fixture missingTaxBrackets = adultFixture(giftDate);
        missingTaxBrackets.taxBrackets = List.of();
        assertExecutionError(
                missingTaxBrackets,
                giftDate,
                SimulationError.TAX_BRACKET_NOT_FOUND
        );

        Fixture missingProductVersion = adultFixture(giftDate);
        missingProductVersion.productVersionAvailable = false;
        assertExecutionError(
                missingProductVersion,
                giftDate,
                SimulationError.PRODUCT_DATA_NOT_READY
        );

        Fixture missingCandidates = adultFixture(giftDate);
        missingCandidates.depositCandidatesAvailable = false;
        assertExecutionError(
                missingCandidates,
                giftDate,
                SimulationError.PRODUCT_CANDIDATE_NOT_FOUND
        );

        // 회귀 방지: 기준 데이터가 일부 누락된 상태로 DRAFT 스냅샷이 생성되지 않아야 한다.
        assertEquals(0, missingDeduction.insertSimulationCount);
        assertEquals(0, missingTaxBrackets.insertSimulationCount);
        assertEquals(0, missingProductVersion.insertSimulationCount);
        assertEquals(0, missingCandidates.insertSimulationCount);
    }

    @Test
    @DisplayName("상위 상품이 부적합해도 뒤 순위의 적합한 예금 상품을 추천한다")
    void recommendEligibleDepositAfterHigherRankedCandidatesAreFilteredOut() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        fixture.depositCandidates = List.of(
                candidate(ProductType.DEPOSIT, 101L, new BigDecimal("4.00")),
                candidate(ProductType.DEPOSIT, 102L, new BigDecimal("3.90")),
                candidate(ProductType.DEPOSIT, 103L, new BigDecimal("3.80")),
                candidate(ProductType.DEPOSIT, 104L, new BigDecimal("3.70"))
        );
        fixture.missingBaseRateProductVersionIds = Set.of(1_101L, 1_102L, 1_103L);

        SimulationResponse response = fixture.execute(80_000_000L, 36, giftDate);

        // 회귀 방지: 수익률 상위 3개가 금리 구간 미비로 제외돼도 4위 적합 상품을 누락하지 않는다.
        assertTrue(response.results().stream()
                .flatMap(result -> result.portfolios().stream())
                .flatMap(portfolio -> portfolio.productCandidates().stream())
                .anyMatch(product -> Objects.equals(product.productId(), 104L)));
    }

    @Test
    @DisplayName("DRAFT 재조회는 현재 기준으로 재계산하지 않고 실행 당시 스냅샷을 반환한다")
    void retrieveDraftWithoutRecalculation() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);
        SimulationService service = fixture.service();
        SimulationResponse executed = service.execute(
                request(80_000_000L, 240, giftDate),
                USER_ID,
                null
        );

        fixture.deductionRuleAvailable = false;
        fixture.taxBrackets = List.of();
        fixture.depositCandidatesAvailable = false;
        SimulationResponse retrieved = service.get(executed.simulationId(), USER_ID);

        // 회귀 방지: 재조회 시 변경된 공제·세율·상품 후보로 과거 결과를 임의 재계산하지 않는다.
        assertEquals(resultSnapshotSignatures(executed), resultSnapshotSignatures(retrieved));
        assertEquals(executed.recommendations(), retrieved.recommendations());
        assertEquals(executed.giftHistorySummary(), retrieved.giftHistorySummary());
    }

    private static Fixture adultFixture(LocalDate giftDate) {
        return new Fixture(giftDate.minusYears(30));
    }

    private static LocalDate futureDate() {
        return LocalDate.now().plusDays(1);
    }

    private static SimulationExecuteRequest request(
            long amount,
            int months,
            LocalDate giftDate
    ) {
        SimulationExecuteRequest request = new SimulationExecuteRequest();
        request.setFamilyId(FAMILY_ID);
        request.setRequestedAmount(amount);
        request.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        request.setInvestmentPeriodMonths(months);
        request.setGiftDate(giftDate);
        return request;
    }

    private static void assertExecutionError(
            Fixture fixture,
            LocalDate giftDate,
            SimulationError expected
    ) {
        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.execute(80_000_000L, 36, giftDate)
        );
        assertEquals(expected, exception.getError());
    }

    private static SimulationResponse.Result result(
            SimulationResponse response,
            ScenarioType scenarioType
    ) {
        return response.results().stream()
                .filter(item -> item.scenarioType() == scenarioType)
                .findFirst()
                .orElseThrow();
    }

    private static List<Long> giftAmounts(SimulationResponse.Result result) {
        return result.tranches().stream()
                .map(SimulationResponse.Tranche::giftAmount)
                .toList();
    }

    private static List<LocalDate> giftDates(SimulationResponse.Result result) {
        return result.tranches().stream()
                .map(SimulationResponse.Tranche::giftDate)
                .toList();
    }

    private static long scenarioEndValue(
            SimulationResponse.Result result,
            RiskProfile profile
    ) {
        long portfolioValue = result.portfolios().stream()
                .filter(portfolio -> portfolio.portfolioType() == profile)
                .findFirst()
                .orElseThrow()
                .expectedFutureValue();
        long uninvested = Math.max(0L,
                result.postTaxAmount() - result.investmentPrincipal());
        return portfolioValue + uninvested;
    }

    private static List<String> resultSnapshotSignatures(SimulationResponse response) {
        return response.results().stream()
                .map(result -> result.scenarioType() + "|"
                        + result.deductionAmount() + "|"
                        + result.taxableAmount() + "|"
                        + result.giftTax() + "|"
                        + result.donorRequiredAmount() + "|"
                        + result.postTaxAmount() + "|"
                        + result.investmentPrincipal() + "|"
                        + result.tranches() + "|"
                        + result.portfolios().stream()
                        .map(portfolio -> portfolio.portfolioId() + ":"
                                + portfolio.portfolioType() + ":"
                                + portfolio.allocation() + ":"
                                + portfolio.expectedFutureValue() + ":"
                                + portfolio.recommended() + ":"
                                + portfolio.productCandidates().stream()
                                .map(product -> product.simulationProductId() + ":"
                                        + product.kbProductVersionId() + ":"
                                        + product.allocatedAmount() + ":"
                                        + product.appliedAnnualRatePercent() + ":"
                                        + product.expectedFutureValue())
                                .toList())
                        .toList())
                .toList();
    }

    private static final class Fixture {
        private static final long SIMULATION_ID = 9_001L;
        private static final long PRODUCT_DATA_VERSION_ID = 51L;

        private final FamilySnapshot family = new FamilySnapshot();
        private final List<GiftHistoryRecord> completedGifts = new ArrayList<>();
        private final List<Query> deductionQueries = new ArrayList<>();
        private final List<SimulationResultRecord> results = new ArrayList<>();
        private final List<SimulationTrancheRecord> tranches = new ArrayList<>();
        private final List<SimulationPortfolioRecord> portfolios = new ArrayList<>();
        private final List<SimulationProductRecord> products = new ArrayList<>();
        private final ProductDataVersionRecord productDataVersion = productDataVersion();
        private SimulationRecord simulation;
        private LocalDate lastCompletedGiftQueryEnd;
        private List<TaxBracket> taxBrackets = taxBrackets();
        private boolean deductionRuleAvailable = true;
        private boolean productVersionAvailable = true;
        private boolean depositCandidatesAvailable = true;
        private List<ProductCandidate> depositCandidates = List.of(candidate(
                ProductType.DEPOSIT, 101L, new BigDecimal("3.40")));
        private List<ProductCandidate> savingsCandidates = List.of(candidate(
                ProductType.SAVINGS, 201L, new BigDecimal("3.10")));
        private BigDecimal depositBaseRate = new BigDecimal("3.40");
        private BigDecimal savingsBaseRate = new BigDecimal("3.10");
        private int baseRateMinimumMonths = 1;
        private int baseRateMaximumMonths = 240;
        private Set<Long> missingBaseRateProductVersionIds = Set.of();
        private int insertSimulationCount;
        private long resultSequence = 9_100L;
        private long trancheSequence = 9_200L;
        private long portfolioSequence = 9_300L;
        private long productSequence = 9_400L;

        private Fixture(LocalDate birthDate) {
            family.setFamilyId(FAMILY_ID);
            family.setUserId(USER_ID);
            family.setFamilyName("김민준");
            family.setRelation("LINEAL_DESCENDANT");
            family.setBirthDate(birthDate);
        }

        private void addCompletedGift(long amount, LocalDate giftDate) {
            GiftHistoryRecord gift = new GiftHistoryRecord();
            gift.setGiftId((long) completedGifts.size() + 1);
            gift.setAmount(amount);
            gift.setGiftDate(giftDate);
            completedGifts.add(gift);
        }

        private void useSafeAssetTerms(int minimumMonths, int maximumMonths) {
            ProductCandidate deposit = candidate(
                    ProductType.DEPOSIT,
                    101L,
                    new BigDecimal("3.40")
            );
            deposit.setMinMonth(minimumMonths);
            deposit.setMaxMonth(maximumMonths);
            ProductCandidate savings = candidate(
                    ProductType.SAVINGS,
                    201L,
                    new BigDecimal("3.10")
            );
            savings.setMinMonth(minimumMonths);
            savings.setMaxMonth(maximumMonths);
            depositCandidates = List.of(deposit);
            savingsCandidates = List.of(savings);
            baseRateMinimumMonths = minimumMonths;
            baseRateMaximumMonths = maximumMonths;
        }

        private SimulationResponse execute(long amount, int months, LocalDate giftDate) {
            return service().execute(request(amount, months, giftDate), USER_ID, null);
        }

        private SimulationService service() {
            return new SimulationService(
                    simulationMapper(),
                    userMapper(),
                    new SimulationCalculator(),
                    new SimulationIdempotencyStore(),
                    new EtfVolatilityCalculator(),
                    com.example.project.support.PiiTestSupport.protectionService()
            );
        }

        private SimulationMapper simulationMapper() {
            return (SimulationMapper) Proxy.newProxyInstance(
                    SimulationMapper.class.getClassLoader(),
                    new Class<?>[]{SimulationMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "selectFamily" -> family;
                        case "selectDeductionRule" -> deductionRule(
                                (Boolean) args[1], (LocalDate) args[2]);
                        case "selectCompletedGifts" -> completedGifts(
                                (LocalDate) args[1], (LocalDate) args[2]);
                        case "selectTaxBrackets" -> taxBrackets;
                        case "selectLatestCompletedProductDataVersion",
                                "selectProductDataVersion" -> productVersionAvailable
                                ? productDataVersion : null;
                        case "selectDepositCandidates" -> depositCandidatesAvailable
                                ? depositCandidates
                                : List.of();
                        case "selectSavingsCandidates" -> savingsCandidates;
                        case "selectBaseRates" ->
                                missingBaseRateProductVersionIds.contains((Long) args[0])
                                        ? List.of()
                                        : List.of(baseRate(
                                        (Long) args[0],
                                        (Long) args[0] == 1_101L
                                                ? depositBaseRate
                                                : savingsBaseRate,
                                        baseRateMinimumMonths,
                                        baseRateMaximumMonths
                                ));
                        case "selectEtfCandidates" -> List.of(etfCandidate(
                                (RiskProfile) args[1]));
                        case "insertSimulation" -> insertSimulation((SimulationRecord) args[0]);
                        case "insertResult" -> insertResult((SimulationResultRecord) args[0]);
                        case "insertTranche" -> insertTranche((SimulationTrancheRecord) args[0]);
                        case "insertPortfolio" -> insertPortfolio(
                                (SimulationPortfolioRecord) args[0]);
                        case "insertProductSnapshot" -> insertProduct(
                                (SimulationProductRecord) args[0]);
                        case "updatePortfolioExpectedFutureValue" -> updatePortfolioValue(
                                (Long) args[0], (Long) args[1]);
                        case "markPortfolioRecommended" -> markRecommended((Long) args[0]);
                        case "deletePortfolio" -> deletePortfolio((Long) args[0]);
                        case "updateDraftVersion" -> updateDraftVersion(
                                (Long) args[0], (Long) args[1], (LocalDateTime) args[2]);
                        case "selectSimulation" -> simulation;
                        case "selectResults" -> List.copyOf(results);
                        case "selectTranches" -> List.copyOf(tranches);
                        case "selectPortfolios" -> List.copyOf(portfolios);
                        case "selectProductSnapshots" -> List.copyOf(products);
                        case "selectRecentEtfPrices", "selectSelectedPreferentialRates" -> List.of();
                        case "toString" -> "SimulationMapperExecuteFixture";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private UserMapper userMapper() {
            return (UserMapper) Proxy.newProxyInstance(
                    UserMapper.class.getClassLoader(),
                    new Class<?>[]{UserMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> user();
                        case "toString" -> "UserMapperExecuteFixture";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private DeductionRule deductionRule(boolean minor, LocalDate date) {
            deductionQueries.add(new Query(date, minor));
            if (!deductionRuleAvailable) {
                return null;
            }
            DeductionRule rule = new DeductionRule();
            rule.setDeductionLimitId(minor ? 1L : 2L);
            rule.setRelation("LINEAL_DESCENDANT");
            rule.setMinor(minor);
            rule.setDeductionLimit(minor
                    ? MINOR_DEDUCTION_LIMIT : ADULT_DEDUCTION_LIMIT);
            rule.setEffectiveFrom(LocalDate.of(2000, 1, 1));
            return rule;
        }

        private List<GiftHistoryRecord> completedGifts(
                LocalDate windowStart,
                LocalDate queryEnd
        ) {
            lastCompletedGiftQueryEnd = queryEnd;
            return completedGifts.stream()
                    .filter(gift -> !gift.getGiftDate().isBefore(windowStart))
                    .filter(gift -> !gift.getGiftDate().isAfter(queryEnd))
                    .toList();
        }

        private int insertSimulation(SimulationRecord record) {
            insertSimulationCount++;
            record.setSimulationId(SIMULATION_ID);
            record.setUserId(USER_ID);
            record.setFamilyName(family.getFamilyName());
            record.setRelation(family.getRelation());
            record.setBirthDate(family.getBirthDate());
            simulation = record;
            return 1;
        }

        private int insertResult(SimulationResultRecord record) {
            record.setResultId(++resultSequence);
            results.add(record);
            return 1;
        }

        private int insertTranche(SimulationTrancheRecord record) {
            record.setTrancheId(++trancheSequence);
            record.setCreatedAt(LocalDateTime.now());
            tranches.add(record);
            return 1;
        }

        private int insertPortfolio(SimulationPortfolioRecord record) {
            record.setPortfolioId(++portfolioSequence);
            SimulationResultRecord result = resultById(record.getResultId());
            record.setSimulationId(SIMULATION_ID);
            record.setScenarioType(result.getScenarioType());
            portfolios.add(record);
            return 1;
        }

        private int insertProduct(SimulationProductRecord record) {
            record.setSimulationProductId(++productSequence);
            products.add(record);
            return 1;
        }

        private int updatePortfolioValue(Long portfolioId, Long futureValue) {
            portfolioById(portfolioId).setExpectedFutureValue(futureValue);
            return 1;
        }

        private int markRecommended(Long portfolioId) {
            portfolioById(portfolioId).setRecommended(true);
            return 1;
        }

        private int deletePortfolio(Long portfolioId) {
            boolean removed = portfolios.removeIf(item -> Objects.equals(
                    item.getPortfolioId(), portfolioId));
            products.removeIf(item -> Objects.equals(item.getPortfolioId(), portfolioId));
            return removed ? 1 : 0;
        }

        private int updateDraftVersion(
                Long simulationId,
                Long expectedVersion,
                LocalDateTime updatedAt
        ) {
            if (simulation == null
                    || !Objects.equals(simulation.getSimulationId(), simulationId)
                    || !Objects.equals(simulation.getVersion(), expectedVersion)) {
                return 0;
            }
            return 1;
        }

        private SimulationResultRecord resultById(Long resultId) {
            return results.stream()
                    .filter(item -> Objects.equals(item.getResultId(), resultId))
                    .findFirst()
                    .orElseThrow();
        }

        private SimulationPortfolioRecord portfolioById(Long portfolioId) {
            return portfolios.stream()
                    .filter(item -> Objects.equals(item.getPortfolioId(), portfolioId))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static ProductCandidate candidate(
            ProductType type,
            long productId,
            BigDecimal rate
    ) {
        ProductCandidate candidate = new ProductCandidate();
        candidate.setProductVersionId(productId + 1_000L);
        candidate.setProductDataVersionId(51L);
        candidate.setProductId(productId);
        candidate.setProductCode(type + "-" + productId);
        candidate.setProductName("테스트 " + type);
        candidate.setProductType(type);
        candidate.setProductCategory(type.name());
        candidate.setBaseAnnualRatePercent(rate);
        candidate.setMaximumAnnualRatePercent(rate);
        candidate.setAppliedAnnualRatePercent(rate);
        candidate.setMinMonth(1);
        candidate.setMaxMonth(240);
        candidate.setMonthlyMaxAmount(null);
        return candidate;
    }

    private static CustomPortfolioRequest customRequest(
            long version,
            long resultId,
            int depositRatio,
            int savingsRatio,
            int etfRatio
    ) {
        CustomPortfolioRequest request = new CustomPortfolioRequest();
        request.setVersion(version);
        request.setResultId(resultId);
        request.setBasePortfolioType(RiskProfile.BALANCED);
        CustomPortfolioRequest.Allocation allocation = new CustomPortfolioRequest.Allocation();
        allocation.setDepositRatio(depositRatio);
        allocation.setSavingsRatio(savingsRatio);
        allocation.setEtfRatio(etfRatio);
        request.setAllocation(allocation);
        return request;
    }

    private static SimulationResponse.Portfolio customPortfolio(SimulationResponse response) {
        return response.results().stream()
                .flatMap(result -> result.portfolios().stream())
                .filter(portfolio -> portfolio.portfolioType() == RiskProfile.CUSTOM)
                .findFirst()
                .orElseThrow();
    }

    private static BaseRateRecord baseRate(
            long productVersionId,
            BigDecimal rate,
            int minimumMonths,
            int maximumMonths
    ) {
        BaseRateRecord tier = new BaseRateRecord();
        tier.setBaseInterestRateId(productVersionId + 10_000L);
        tier.setProductVersionId(productVersionId);
        tier.setMinimumMonths(minimumMonths);
        tier.setMaximumMonths(maximumMonths);
        tier.setBaseRatePercent(rate);
        tier.setMaximumRatePercent(rate);
        return tier;
    }

    private static ProductCandidate etfCandidate(RiskProfile profile) {
        BigDecimal rate = switch (profile) {
            case CONSERVATIVE -> new BigDecimal("3.00");
            case BALANCED -> new BigDecimal("5.00");
            case AGGRESSIVE -> new BigDecimal("8.00");
            case CUSTOM -> throw new IllegalArgumentException("CUSTOM is not a preset profile");
        };
        ProductCandidate candidate = candidate(
                ProductType.ETF,
                300L + profile.ordinal(),
                rate
        );
        candidate.setRiskLevel(profile.name());
        candidate.setAnnualizedReturn10yPercent(rate);
        return candidate;
    }

    private static ProductDataVersionRecord productDataVersion() {
        ProductDataVersionRecord version = new ProductDataVersionRecord();
        version.setProductDataVersionId(51L);
        version.setVersionCode("20260812-01");
        version.setDataDate(LocalDate.now());
        version.setStatus("COMPLETED");
        return version;
    }

    private static List<TaxBracket> taxBrackets() {
        TaxBracket first = new TaxBracket();
        first.setBracketId(1L);
        first.setLowerBound(0L);
        first.setUpperBound(100_000_000L);
        first.setTaxRate(new BigDecimal("0.10"));
        first.setProgressiveDeduction(0L);

        TaxBracket second = new TaxBracket();
        second.setBracketId(2L);
        second.setLowerBound(100_000_000L);
        second.setUpperBound(500_000_000L);
        second.setTaxRate(new BigDecimal("0.20"));
        second.setProgressiveDeduction(10_000_000L);
        return List.of(first, second);
    }

    private static UserVO user() {
        UserVO user = new UserVO();
        user.setUserId(USER_ID);
        return user;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private record Query(LocalDate date, boolean minor) {
    }
}
