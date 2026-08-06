package com.example.project.simulation.service;

import com.example.project.simulation.domain.FamilySnapshot;
import com.example.project.simulation.domain.PreferentialRateRecord;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.ProductVersionDetailRecord;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxPaymentMethod;
import com.example.project.simulation.dto.request.SimulationSaveRequest;
import com.example.project.simulation.dto.response.SimulationSaveResponse;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServiceSaveTest {

    private static final long USER_ID = 7L;
    private static final long PREVIOUS_SIMULATION_ID = 9_000L;
    private static final long SIMULATION_ID = 9_001L;
    private static final long PORTFOLIO_ID = 101L;
    private static final long PRODUCT_ID = 1_001L;

    @Test
    @DisplayName("선택 상품과 우대금리를 서버에서 재계산해 SAVED로 저장한다")
    void saveSelectedPortfolio() {
        Fixture fixture = new Fixture();

        SimulationSaveResponse response = fixture.service().save(
                SIMULATION_ID,
                fixture.request(),
                USER_ID,
                "save-request-1"
        );

        assertEquals(SimulationStatus.SAVED, response.status());
        assertEquals(2L, response.version());
        assertEquals(1_040L, response.serverCalculation().expectedFutureValue());
        assertEquals(40L, response.serverCalculation().expectedProfit());
        assertEquals(1_000L, response.selection().allocation().depositAmount());
        assertEquals(new BigDecimal("4.00"), response.selection()
                .selectedProducts().get(0).appliedAnnualRatePercent());
        assertEquals(1, fixture.saveCount.get());
        assertEquals(1, fixture.markSelectedCount.get());
    }

    @Test
    @DisplayName("같은 멱등성 키와 요청은 최초 저장 응답을 재사용한다")
    void reuseIdempotentSaveResponse() {
        Fixture fixture = new Fixture();
        SimulationSaveRequest request = fixture.request();
        SimulationService service = fixture.service();

        SimulationSaveResponse first = service.save(
                SIMULATION_ID, request, USER_ID, "same-key");
        SimulationSaveResponse second = service.save(
                SIMULATION_ID, request, USER_ID, "same-key");

        assertSame(first, second);
        assertEquals(1, fixture.saveCount.get());
    }

    @Test
    @DisplayName("실행 당시와 다른 상품 데이터 버전의 상품은 저장하지 않는다")
    void rejectDifferentProductDataVersion() {
        Fixture fixture = new Fixture();
        fixture.detail.setProductDataVersionId(99L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID,
                        fixture.request(),
                        USER_ID,
                        null
                )
        );

        assertEquals(SimulationError.PRODUCT_DATA_VERSION_MISMATCH,
                exception.getError());
        assertEquals(0, fixture.saveCount.get());
    }

    @Test
    @DisplayName("선택 상품 갱신이 반영되지 않으면 저장 실패로 처리한다")
    void failWhenSelectedProductWasNotUpdated() {
        Fixture fixture = new Fixture();
        fixture.markSelectedResult = 0;

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID,
                        fixture.request(),
                        USER_ID,
                        null
                )
        );

        assertEquals(SimulationError.SIMULATION_SAVE_FAILED,
                exception.getError());
        assertEquals(0, fixture.saveCount.get());
    }

    @Test
    @DisplayName("새 결과로 교체해도 기존 저장 이력의 선택 결과는 보존한다")
    void preservePreviousSavedSelectionWhenReplacing() {
        Fixture fixture = new Fixture();
        fixture.activeSaved = previousSavedSimulation();
        SimulationSaveRequest request = fixture.request();
        request.setReplaceExistingSaved(true);
        request.setExpectedExistingSavedSimulationId(PREVIOUS_SIMULATION_ID);

        SimulationSaveResponse response = fixture.service().save(
                SIMULATION_ID,
                request,
                USER_ID,
                "replace-save-request"
        );

        assertTrue(response.replacement().replaced());
        assertEquals(
                PREVIOUS_SIMULATION_ID,
                response.replacement().previousSimulation().simulationId()
        );
        assertEquals(List.of(PREVIOUS_SIMULATION_ID), fixture.resetSimulationIds);
        assertEquals(List.of(SIMULATION_ID), fixture.clearedSimulationIds);
        assertEquals(List.of(SIMULATION_ID), fixture.deletedConditionSimulationIds);
        assertEquals(List.of(PRODUCT_ID), fixture.restoredProductIds);
    }

    private static final class Fixture {
        private final SimulationRecord simulation = simulation();
        private final FamilySnapshot family = family();
        private final SimulationPortfolioRecord portfolio = portfolio();
        private final SimulationResultRecord result = result();
        private final SimulationTrancheRecord tranche = tranche();
        private final SimulationProductRecord product = product();
        private final ProductVersionDetailRecord detail = detail();
        private final PreferentialRateRecord preferentialRate = preferentialRate();
        private final AtomicInteger saveCount = new AtomicInteger();
        private final AtomicInteger markSelectedCount = new AtomicInteger();
        private final List<Long> resetSimulationIds = new ArrayList<>();
        private final List<Long> clearedSimulationIds = new ArrayList<>();
        private final List<Long> deletedConditionSimulationIds = new ArrayList<>();
        private final List<Long> restoredProductIds = new ArrayList<>();
        private SimulationRecord activeSaved;
        private int markSelectedResult = 1;

        private SimulationSaveRequest request() {
            SimulationSaveRequest request = new SimulationSaveRequest();
            request.setVersion(1L);
            request.setSelectedPortfolioId(PORTFOLIO_ID);
            request.setReplaceExistingSaved(false);

            SimulationSaveRequest.ProductSelection selection =
                    new SimulationSaveRequest.ProductSelection();
            selection.setSimulationProductId(PRODUCT_ID);
            selection.setPreferentialConditionCodes(List.of("SALARY"));
            request.setProductSelections(List.of(selection));
            return request;
        }

        private SimulationService service() {
            SimulationMapper mapper = (SimulationMapper) Proxy.newProxyInstance(
                    SimulationMapper.class.getClassLoader(),
                    new Class<?>[]{SimulationMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "selectSimulation" -> simulation;
                        case "lockFamily" -> family;
                        case "selectSavedSimulationByFamily" -> activeSaved;
                        case "selectPortfolio" -> portfolio;
                        case "selectPortfolioProducts" -> List.of(product);
                        case "selectProductVersionDetail" -> detail;
                        case "selectPreferentialRatesByCodes" ->
                                List.of(preferentialRate);
                        case "selectPortfolios" -> List.of(portfolio);
                        case "selectResults" -> List.of(result);
                        case "selectTranches" -> List.of(tranche);
                        case "restoreSimulationProduct" -> {
                            restoredProductIds.add((Long) args[0]);
                            yield 1;
                        }
                        case "clearSimulationSelections" -> {
                            clearedSimulationIds.add((Long) args[0]);
                            yield 1;
                        }
                        case "deleteSimulationPreferentialConditions" -> {
                            deletedConditionSimulationIds.add((Long) args[0]);
                            yield 1;
                        }
                        case "insertSelectedPreferentialCondition" -> 1;
                        case "resetSavedSimulation" -> {
                            resetSimulationIds.add((Long) args[0]);
                            yield 1;
                        }
                        case "markSimulationProductSelected" -> {
                            markSelectedCount.incrementAndGet();
                            yield markSelectedResult;
                        }
                        case "saveSimulation" -> {
                            saveCount.incrementAndGet();
                            yield 1;
                        }
                        case "toString" -> "SimulationMapperSaveFixture";
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
                        case "toString" -> "UserMapperSaveFixture";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
            return new SimulationService(
                    mapper,
                    userMapper,
                    new SimulationCalculator(),
                    new SimulationIdempotencyStore()
            );
        }
    }

    private static SimulationRecord simulation() {
        SimulationRecord simulation = new SimulationRecord();
        simulation.setSimulationId(SIMULATION_ID);
        simulation.setProductDataVersionId(51L);
        simulation.setFamilyId(3L);
        simulation.setUserId(USER_ID);
        simulation.setRequestedAmount(1_000L);
        simulation.setStatus(SimulationStatus.DRAFT);
        simulation.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        simulation.setInvestmentPeriodMonths(12);
        simulation.setAsOfDate(LocalDate.of(2026, 8, 5));
        simulation.setInvestmentEndDate(LocalDate.of(2027, 8, 5));
        simulation.setVersion(1L);
        simulation.setExpiredAt(LocalDateTime.now().plusHours(1));
        return simulation;
    }

    private static FamilySnapshot family() {
        FamilySnapshot family = new FamilySnapshot();
        family.setFamilyId(3L);
        family.setUserId(USER_ID);
        return family;
    }

    private static SimulationRecord previousSavedSimulation() {
        SimulationRecord simulation = simulation();
        simulation.setSimulationId(PREVIOUS_SIMULATION_ID);
        simulation.setStatus(SimulationStatus.SAVED);
        simulation.setSelectedPortfolioId(91L);
        simulation.setVersion(4L);
        simulation.setSavedAt(LocalDateTime.of(2026, 8, 1, 9, 30));
        simulation.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 9, 30));
        simulation.setExpiredAt(null);
        return simulation;
    }

    private static SimulationPortfolioRecord portfolio() {
        SimulationPortfolioRecord portfolio = new SimulationPortfolioRecord();
        portfolio.setPortfolioId(PORTFOLIO_ID);
        portfolio.setResultId(201L);
        portfolio.setSimulationId(SIMULATION_ID);
        portfolio.setScenarioType(ScenarioType.IMMEDIATE);
        portfolio.setPortfolioType(RiskProfile.CONSERVATIVE);
        portfolio.setDepositAmount(1_000L);
        portfolio.setSavingsAmount(0L);
        portfolio.setEtfAmount(0L);
        portfolio.setExpectedFutureValue(1_030L);
        portfolio.setRecommended(true);
        return portfolio;
    }

    private static SimulationResultRecord result() {
        SimulationResultRecord result = new SimulationResultRecord();
        result.setResultId(201L);
        result.setSimulationId(SIMULATION_ID);
        result.setScenarioType(ScenarioType.IMMEDIATE);
        result.setGiftTax(0L);
        result.setDonorRequiredAmount(1_000L);
        result.setInvestmentPrincipal(1_000L);
        return result;
    }

    private static SimulationTrancheRecord tranche() {
        SimulationTrancheRecord tranche = new SimulationTrancheRecord();
        tranche.setTrancheId(301L);
        tranche.setResultId(201L);
        tranche.setSequenceNo(1);
        tranche.setGiftDate(LocalDate.of(2026, 8, 5));
        tranche.setInvestmentAmount(1_000L);
        return tranche;
    }

    private static SimulationProductRecord product() {
        SimulationProductRecord product = new SimulationProductRecord();
        product.setSimulationProductId(PRODUCT_ID);
        product.setPortfolioId(PORTFOLIO_ID);
        product.setProductVersionId(401L);
        product.setProductId(501L);
        product.setProductName("테스트 정기예금");
        product.setProductType(ProductType.DEPOSIT);
        product.setAllocatedAmount(1_000L);
        product.setBaseAnnualRatePercent(new BigDecimal("3.00"));
        product.setMaximumAnnualRatePercent(new BigDecimal("4.00"));
        product.setAppliedAnnualRatePercent(new BigDecimal("3.00"));
        product.setMinimumContractMonths(1);
        product.setMaximumContractMonths(60);
        product.setExpectedFutureValue(1_030L);
        return product;
    }

    private static ProductVersionDetailRecord detail() {
        ProductVersionDetailRecord detail = new ProductVersionDetailRecord();
        detail.setProductVersionId(401L);
        detail.setProductDataVersionId(51L);
        detail.setProductType(ProductType.DEPOSIT);
        detail.setMinimumAmount(1L);
        detail.setMaximumAmount(10_000L);
        detail.setMinimumMonths(1);
        detail.setMaximumMonths(60);
        return detail;
    }

    private static PreferentialRateRecord preferentialRate() {
        PreferentialRateRecord rate = new PreferentialRateRecord();
        rate.setPreferentialInterestRateId(601L);
        rate.setProductVersionId(401L);
        rate.setConditionCode("SALARY");
        rate.setAdditionalRatePercent(new BigDecimal("1.00"));
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
