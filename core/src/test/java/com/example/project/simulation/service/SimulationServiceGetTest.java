package com.example.project.simulation.service;

import com.example.project.simulation.domain.ProductDataVersionRecord;
import com.example.project.simulation.domain.BaseRateRecord;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.PreferentialRateRecord;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxPaymentMethod;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationServiceGetTest {

    private static final long USER_ID = 7L;
    private static final long SIMULATION_ID = 9_001L;

    @Test
    @DisplayName("DRAFT 단건 조회는 실행 당시 스냅샷과 선택되지 않은 결과를 반환한다")
    void getDraftSnapshot() {
        Fixture fixture = new Fixture();

        SimulationResponse response = fixture.service().get(SIMULATION_ID, USER_ID);

        assertEquals(SIMULATION_ID, response.simulationId());
        assertEquals(SimulationStatus.DRAFT, response.status());
        assertEquals(20_000_000L, response.giftHistorySummary().previousGiftAmount());
        assertEquals(2, response.results().size());
        assertEquals(3, response.recommendations().size());
        assertNull(response.selection());
    }

    @Test
    @DisplayName("미래 증여 예정일로 생성한 DRAFT 스냅샷을 조회한다")
    void getDraftSnapshotWithFutureGiftDate() {
        Fixture fixture = new Fixture();
        LocalDate futureGiftDate = LocalDate.of(2026, 9, 4);
        fixture.simulation.setGiftDate(futureGiftDate);
        fixture.simulation.setInvestmentEndDate(futureGiftDate.plusMonths(36));
        fixture.tranches.forEach(tranche -> tranche.setGiftDate(futureGiftDate));

        SimulationResponse response = fixture.service().get(SIMULATION_ID, USER_ID);

        assertEquals(LocalDate.of(2026, 8, 4), response.input().asOfDate());
        assertEquals(futureGiftDate, response.input().giftDate());
        assertEquals(LocalDate.of(2016, 9, 4),
                response.giftHistorySummary().lookbackStartDate());
    }

    @Test
    @DisplayName("API 필수 시나리오와 포트폴리오가 일부 누락되면 충돌로 처리한다")
    void rejectPartialDraftSnapshot() {
        Fixture fixture = new Fixture();
        fixture.results.removeIf(item -> item.getScenarioType()
                == ScenarioType.TAX_OPTIMIZED);
        fixture.tranches.removeIf(item -> item.getResultId() != 1L);
        fixture.portfolios.removeIf(item -> item.getPortfolioId() != 100L);
        fixture.products.removeIf(item -> item.getPortfolioId() != 100L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        assertEquals(
                SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE,
                exception.getError()
        );
    }

    @Test
    @DisplayName("0.5.8에 저장된 증여 스냅샷으로 파생 필드를 복원한다")
    void deriveFieldsFromPersistedGiftSnapshot() {
        Fixture fixture = new Fixture();

        SimulationResponse response = fixture.service().get(SIMULATION_ID, USER_ID);

        assertEquals(26, response.family().ageAtSimulation());
        assertEquals(false, response.family().minorAtSimulation());
        assertEquals(LocalDate.of(2016, 8, 4),
                response.giftHistorySummary().lookbackStartDate());
        assertEquals(20_000_000L,
                response.giftHistorySummary().usedDeductionAmount());
        assertEquals(30_000_000L,
                response.giftHistorySummary().remainingDeductionAmount());
    }

    @Test
    @DisplayName("만료된 DRAFT는 조회할 수 없다")
    void rejectExpiredDraft() {
        Fixture fixture = new Fixture();
        fixture.simulation.setExpiredAt(LocalDateTime.now().minusSeconds(1));

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        assertEquals(SimulationError.SIMULATION_EXPIRED, exception.getError());
    }

    @Test
    @DisplayName("다른 사용자의 시뮬레이션은 조회할 수 없다")
    void rejectOtherUsersSimulation() {
        Fixture fixture = new Fixture();
        fixture.simulation.setUserId(99L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        assertEquals(SimulationError.SIMULATION_ACCESS_DENIED, exception.getError());
    }

    @Test
    @DisplayName("시나리오가 누락된 실행 스냅샷은 충돌로 처리한다")
    void rejectIncompleteSnapshot() {
        Fixture fixture = new Fixture();
        fixture.results.remove(1);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        assertEquals(
                SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE,
                exception.getError()
        );
    }

    @Test
    @DisplayName("증여 회차 순번이 1부터 연속되지 않으면 불완전한 스냅샷으로 처리한다")
    void rejectInvalidTrancheSequence() {
        Fixture fixture = new Fixture();
        fixture.tranches.get(0).setSequenceNo(2);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        assertEquals(
                SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE,
                exception.getError()
        );
    }

    @Test
    @DisplayName("SAVED 단건 조회는 선택 포트폴리오와 선택 상품을 복원한다")
    void getSavedSelection() {
        Fixture fixture = new Fixture();
        SimulationPortfolioRecord selectedPortfolio = fixture.portfolios.get(0);
        fixture.simulation.setStatus(SimulationStatus.SAVED);
        fixture.simulation.setSelectedPortfolioId(selectedPortfolio.getPortfolioId());
        fixture.simulation.setSavedAt(LocalDateTime.now().minusMinutes(1));
        fixture.products.get(0).setSelected(true);
        fixture.selectedPreferentialRates = List.of(preferentialRate());

        SimulationResponse response = fixture.service().get(SIMULATION_ID, USER_ID);

        assertEquals(selectedPortfolio.getPortfolioId(), response.selection().selectedPortfolioId());
        assertEquals(1, response.selection().selectedProducts().size());
        assertEquals(900L, response.selection().investmentPrincipal());
        assertEquals(
                "SALARY",
                response.results().get(0).portfolios().get(0).productCandidates().get(0)
                        .selectedPreferentialConditions().get(0).conditionCode()
        );
    }

    @Test
    @DisplayName("DRAFT에 과거 확정 포트폴리오가 남아 있으면 불완전한 상태로 처리한다")
    void rejectDraftWithPreviousSavedSelection() {
        Fixture fixture = new Fixture();
        SimulationPortfolioRecord selectedPortfolio = fixture.portfolios.get(0);
        fixture.simulation.setSelectedPortfolioId(selectedPortfolio.getPortfolioId());
        fixture.products.get(0).setSelected(true);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        // 회귀 방지: SAVED를 DRAFT로 되돌린 뒤 과거 확정 선택이 화면에 다시 노출되지 않아야 한다.
        assertEquals(SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE,
                exception.getError());
    }

    @Test
    @DisplayName("SAVED 선택 상품의 배분액이 포트폴리오와 다르면 충돌로 처리한다")
    void rejectIncompleteSavedSelection() {
        Fixture fixture = new Fixture();
        SimulationPortfolioRecord selectedPortfolio = fixture.portfolios.get(0);
        SimulationProductRecord selectedProduct = fixture.products.get(0);
        fixture.simulation.setStatus(SimulationStatus.SAVED);
        fixture.simulation.setSelectedPortfolioId(selectedPortfolio.getPortfolioId());
        fixture.simulation.setSavedAt(LocalDateTime.now().minusMinutes(1));
        selectedProduct.setSelected(true);
        SimulationProductRecord duplicateSelection = product(
                999L,
                selectedPortfolio.getPortfolioId(),
                900L
        );
        duplicateSelection.setSelected(true);
        fixture.products.add(duplicateSelection);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        assertEquals(
                SimulationError.SAVED_SELECTION_INCOMPLETE,
                exception.getError()
        );
    }

    @Test
    @DisplayName("존재하지 않는 시뮬레이션을 조회하면 NOT_FOUND로 처리한다")
    void rejectMissingSimulation() {
        Fixture fixture = new Fixture();
        fixture.simulationAvailable = false;

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        // 회귀 방지: 삭제되었거나 잘못된 ID를 빈 정상 응답으로 반환하지 않는다.
        assertEquals(SimulationError.SIMULATION_NOT_FOUND, exception.getError());
    }

    @Test
    @DisplayName("DRAFT에 저장 완료 시각이 남아 있으면 불완전한 상태로 처리한다")
    void rejectDraftWithSavedTimestamp() {
        Fixture fixture = new Fixture();
        fixture.simulation.setSavedAt(LocalDateTime.now().minusMinutes(1));

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        // 회귀 방지: SAVED를 DRAFT로 되돌릴 때 saved_at 정리가 누락된 상태를 노출하지 않는다.
        assertEquals(SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE,
                exception.getError());
    }

    @Test
    @DisplayName("한 투자 성향에 추천 포트폴리오가 둘 이상이면 조회를 거부한다")
    void rejectDuplicateRecommendationForProfile() {
        Fixture fixture = new Fixture();
        fixture.portfolios.stream()
                .filter(item -> item.getScenarioType() == ScenarioType.TAX_OPTIMIZED)
                .filter(item -> item.getPortfolioType() == RiskProfile.CONSERVATIVE)
                .findFirst()
                .orElseThrow()
                .setRecommended(true);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        // 회귀 방지: 동일 성향에 상충하는 추천 시나리오 두 건이 프론트로 전달되지 않게 한다.
        assertEquals(SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE,
                exception.getError());
    }

    @Test
    @DisplayName("상품 스냅샷 배분액이 포트폴리오 배분과 다르면 조회를 거부한다")
    void rejectProductAllocationSnapshotMismatch() {
        Fixture fixture = new Fixture();
        fixture.products.get(0).setAllocatedAmount(899L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        // 회귀 방지: 일부 상품 스냅샷이 손상된 결과에서 잘못된 미래가치를 보여주지 않는다.
        assertEquals(SimulationError.SIMULATION_SNAPSHOT_INCOMPLETE,
                exception.getError());
    }

    @Test
    @DisplayName("SAVED인데 선택 상품이 없으면 선택 스냅샷 불완전으로 처리한다")
    void rejectSavedSimulationWithoutSelectedProduct() {
        Fixture fixture = new Fixture();
        SimulationPortfolioRecord selectedPortfolio = fixture.portfolios.get(0);
        fixture.simulation.setStatus(SimulationStatus.SAVED);
        fixture.simulation.setSelectedPortfolioId(selectedPortfolio.getPortfolioId());
        fixture.simulation.setSavedAt(LocalDateTime.now().minusMinutes(1));

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().get(SIMULATION_ID, USER_ID)
        );

        // 회귀 방지: 확정 포트폴리오만 있고 실제 선택 상품이 사라진 SAVED를 정상 처리하지 않는다.
        assertEquals(SimulationError.SAVED_SELECTION_INCOMPLETE,
                exception.getError());
    }

    private static final class Fixture {
        private final SimulationRecord simulation = simulation();
        private final List<SimulationResultRecord> results = new ArrayList<>();
        private final List<SimulationTrancheRecord> tranches = new ArrayList<>();
        private final List<SimulationPortfolioRecord> portfolios = new ArrayList<>();
        private final List<SimulationProductRecord> products = new ArrayList<>();
        private final ProductDataVersionRecord productDataVersion = productDataVersion();
        private List<PreferentialRateRecord> selectedPreferentialRates = List.of();
        private boolean simulationAvailable = true;

        private Fixture() {
            addScenario(1L, ScenarioType.IMMEDIATE, 900L, true, 100L);
            addScenario(2L, ScenarioType.TAX_OPTIMIZED, 900L, false, 200L);
        }

        private void addScenario(
                long resultId,
                ScenarioType scenarioType,
                long principal,
                boolean recommended,
                long firstPortfolioId
        ) {
            SimulationResultRecord result = new SimulationResultRecord();
            result.setResultId(resultId);
            result.setSimulationId(SIMULATION_ID);
            result.setScenarioType(scenarioType);
            result.setDeductionAmount(0L);
            result.setTaxableAmount(0L);
            result.setGiftTax(100L);
            result.setDonorRequiredAmount(principal + 100L);
            result.setPostTaxAmount(principal);
            result.setInvestmentPrincipal(principal);
            results.add(result);

            SimulationTrancheRecord tranche = new SimulationTrancheRecord();
            tranche.setTrancheId(resultId * 10);
            tranche.setResultId(resultId);
            tranche.setSequenceNo(1);
            tranche.setGiftDate(LocalDate.of(2026, 8, 4));
            tranche.setGiftAmount(principal + 100L);
            tranche.setEstimatedGiftTax(100L);
            tranche.setDonorRequiredAmount(principal + 100L);
            tranche.setInvestmentAmount(principal);
            tranche.setCreatedAt(LocalDateTime.now().minusHours(1));
            tranches.add(tranche);

            int index = 0;
            for (RiskProfile profile : RiskProfile.values()) {
                long portfolioId = firstPortfolioId + index;
                SimulationPortfolioRecord portfolio = new SimulationPortfolioRecord();
                portfolio.setPortfolioId(portfolioId);
                portfolio.setResultId(resultId);
                portfolio.setSimulationId(SIMULATION_ID);
                portfolio.setScenarioType(scenarioType);
                portfolio.setPortfolioType(profile);
                portfolio.setDepositAmount(principal);
                portfolio.setSavingsAmount(0L);
                portfolio.setEtfAmount(0L);
                portfolio.setExpectedFutureValue(principal + 100L);
                portfolio.setRecommended(recommended);
                portfolios.add(portfolio);
                products.add(product(portfolioId, portfolioId, principal));
                index++;
            }
        }

        private SimulationService service() {
            SimulationMapper mapper = (SimulationMapper) Proxy.newProxyInstance(
                    SimulationMapper.class.getClassLoader(),
                    new Class<?>[]{SimulationMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "selectSimulation" -> simulationAvailable ? simulation : null;
                        case "selectResults" -> results;
                        case "selectTranches" -> tranches;
                        case "selectPortfolios" -> portfolios;
                        case "selectProductSnapshots" -> products;
                        case "selectProductDataVersion" -> productDataVersion;
                        case "selectSelectedPreferentialRates" -> selectedPreferentialRates;
                        case "selectBaseRates" -> List.of(baseRate((Long) args[0]));
                        case "toString" -> "SimulationMapperFixture";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
            UserMapper userMapper = (UserMapper) Proxy.newProxyInstance(
                    UserMapper.class.getClassLoader(),
                    new Class<?>[]{UserMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> user();
                        case "toString" -> "UserMapperFixture";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
            return new SimulationService(
                    mapper,
                    userMapper,
                    new SimulationCalculator(),
                    new SimulationIdempotencyStore(),
                    new EtfVolatilityCalculator(),
                    com.example.project.support.PiiTestSupport.protectionService()
            );
        }
    }

    private static SimulationRecord simulation() {
        SimulationRecord simulation = new SimulationRecord();
        simulation.setSimulationId(SIMULATION_ID);
        simulation.setProductDataVersionId(51L);
        simulation.setFamilyId(3L);
        simulation.setUserId(USER_ID);
        simulation.setFamilyName("김수증");
        simulation.setRelation("LINEAL_DESCENDANT");
        simulation.setBirthDate(LocalDate.of(2000, 1, 1));
        simulation.setRequestedAmount(1_000L);
        simulation.setStatus(SimulationStatus.DRAFT);
        simulation.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        simulation.setInvestmentPeriodMonths(36);
        simulation.setAsOfDate(LocalDate.of(2026, 8, 4));
        simulation.setGiftDate(LocalDate.of(2026, 8, 4));
        simulation.setInvestmentEndDate(LocalDate.of(2029, 8, 4));
        simulation.setPreviousGiftAmount(20_000_000L);
        simulation.setDeductionLimit(50_000_000L);
        simulation.setDeductionRenewalDate(LocalDate.of(2028, 1, 1));
        simulation.setCalculationVersion(SimulationService.CALCULATION_VERSION);
        simulation.setFormulaVersion(SimulationService.FORMULA_VERSION);
        simulation.setVersion(0L);
        simulation.setCreatedAt(LocalDateTime.now().minusHours(1));
        simulation.setUpdatedAt(LocalDateTime.now().minusHours(1));
        simulation.setExpiredAt(LocalDateTime.now().plusHours(23));
        return simulation;
    }

    private static ProductDataVersionRecord productDataVersion() {
        ProductDataVersionRecord version = new ProductDataVersionRecord();
        version.setProductDataVersionId(51L);
        version.setVersionCode("20260804-01");
        version.setDataDate(LocalDate.of(2026, 8, 4));
        version.setStatus("COMPLETED");
        return version;
    }

    private static PreferentialRateRecord preferentialRate() {
        PreferentialRateRecord rate = new PreferentialRateRecord();
        rate.setPreferentialInterestRateId(601L);
        rate.setProductVersionId(1_100L);
        rate.setConditionCode("SALARY");
        rate.setAdditionalRatePercent(new BigDecimal("0.50"));
        rate.setDescription("급여 이체");
        return rate;
    }

    private static SimulationProductRecord product(
            long simulationProductId,
            long portfolioId,
            long allocatedAmount
    ) {
        SimulationProductRecord product = new SimulationProductRecord();
        product.setSimulationProductId(simulationProductId);
        product.setPortfolioId(portfolioId);
        product.setProductVersionId(1_000L + simulationProductId);
        product.setProductId(2_000L + simulationProductId);
        product.setProductCode("DEP-" + simulationProductId);
        product.setProductName("테스트 예금 " + simulationProductId);
        product.setProductType(ProductType.DEPOSIT);
        product.setProductCategory("DEPOSIT");
        product.setAllocatedAmount(allocatedAmount);
        product.setBaseAnnualRatePercent(new BigDecimal("3.00"));
        product.setMaximumAnnualRatePercent(new BigDecimal("3.50"));
        product.setAppliedAnnualRatePercent(new BigDecimal("3.00"));
        product.setExpectedFutureValue(allocatedAmount + 100L);
        return product;
    }

    private static BaseRateRecord baseRate(long productVersionId) {
        BaseRateRecord rate = new BaseRateRecord();
        rate.setBaseInterestRateId(productVersionId + 10_000L);
        rate.setProductVersionId(productVersionId);
        rate.setMinimumMonths(1);
        rate.setMaximumMonths(240);
        rate.setBaseRatePercent(new BigDecimal("3.00"));
        rate.setMaximumRatePercent(new BigDecimal("3.50"));
        return rate;
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
}
