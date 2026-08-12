package com.example.project.simulation.service;

import com.example.project.simulation.domain.DeductionRule;
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
import com.example.project.simulation.dto.response.SimulationResponse;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServiceExecuteTest {

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
        assertEquals(List.of(20_000_000L, 50_000_000L), giftAmounts(optimized));
        assertEquals(70_000_000L, optimized.deductionAmount());
        assertEquals(0L, optimized.giftTax());
        assertTrue(fixture.deductionQueries.stream().anyMatch(Query::minor));
        assertTrue(fixture.deductionQueries.stream().anyMatch(query -> !query.minor()));
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
        assertEquals(2_000_000L, optimized.giftTax());
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
        assertEquals(1_000_000L, optimized.giftTax());
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
        assertEquals(3_000_000L, optimized.giftTax());
    }

    @Test
    @DisplayName("추천 시나리오는 동일 성향에서 종료 시점 총 가치가 큰 쪽을 선택한다")
    void recommendScenarioWithGreaterFutureValue() {
        LocalDate giftDate = futureDate();
        Fixture fixture = adultFixture(giftDate);

        SimulationResponse response = fixture.execute(80_000_000L, 240, giftDate);

        // 회귀 방지: 절세 여부만으로 고정 추천하지 않고 운용 종료 시점의 총 가치를 비교한다.
        for (RiskProfile profile : RiskProfile.values()) {
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

    private static Fixture adultFixture(LocalDate giftDate) {
        return new Fixture(giftDate.minusYears(30));
    }

    private static LocalDate futureDate() {
        return LocalDate.now().plusDays(1);
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

        private SimulationResponse execute(long amount, int months, LocalDate giftDate) {
            SimulationExecuteRequest request = new SimulationExecuteRequest();
            request.setFamilyId(FAMILY_ID);
            request.setRequestedAmount(amount);
            request.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
            request.setInvestmentPeriodMonths(months);
            request.setGiftDate(giftDate);
            return service().execute(request, USER_ID, null);
        }

        private SimulationService service() {
            return new SimulationService(
                    simulationMapper(),
                    userMapper(),
                    new SimulationCalculator(),
                    new SimulationIdempotencyStore(),
                    new EtfVolatilityCalculator()
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
                        case "selectTaxBrackets" -> taxBrackets();
                        case "selectLatestCompletedProductDataVersion",
                                "selectProductDataVersion" -> productDataVersion;
                        case "selectDepositCandidates" -> List.of(candidate(
                                ProductType.DEPOSIT, 101L, new BigDecimal("3.40")));
                        case "selectSavingsCandidates" -> List.of(candidate(
                                ProductType.SAVINGS, 201L, new BigDecimal("3.10")));
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

    private static ProductCandidate etfCandidate(RiskProfile profile) {
        BigDecimal rate = switch (profile) {
            case CONSERVATIVE -> new BigDecimal("3.00");
            case BALANCED -> new BigDecimal("5.00");
            case AGGRESSIVE -> new BigDecimal("8.00");
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
