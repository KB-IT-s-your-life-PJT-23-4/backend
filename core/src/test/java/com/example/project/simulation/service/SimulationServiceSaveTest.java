package com.example.project.simulation.service;

import com.example.project.simulation.domain.FamilySnapshot;
import com.example.project.simulation.domain.BaseRateRecord;
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
import java.util.Map;
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
    @DisplayName("새 결과로 교체하면 기존 SAVED의 선택 정보를 제거하고 DRAFT로 되돌린다")
    void clearPreviousSavedSelectionWhenReplacing() {
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
        assertEquals(List.of(PREVIOUS_SIMULATION_ID, SIMULATION_ID),
                fixture.clearedSimulationIds);
        assertEquals(List.of(PREVIOUS_SIMULATION_ID, SIMULATION_ID),
                fixture.deletedConditionSimulationIds);
        // 기존 SAVED 상품을 실행 당시 기본 계산값으로 복구한 후 새 선택 상품을 다시 계산한다.
        assertEquals(List.of(PRODUCT_ID, PRODUCT_ID), fixture.restoredProductIds);
    }

    @Test
    @DisplayName("만료된 DRAFT는 최종 저장할 수 없다")
    void rejectExpiredDraftOnSave() {
        Fixture fixture = new Fixture();
        fixture.simulation.setExpiredAt(LocalDateTime.now().minusSeconds(1));

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: 보존 기간이 끝난 계산 결과가 뒤늦게 확정되는 것을 막는다.
        assertEquals(SimulationError.SIMULATION_EXPIRED, exception.getError());
        assertEquals(0, fixture.saveCount.get());
    }

    @Test
    @DisplayName("조회한 버전보다 시뮬레이션 버전이 높으면 저장을 거부한다")
    void rejectStaleSimulationVersion() {
        Fixture fixture = new Fixture();
        fixture.simulation.setVersion(3L);
        SimulationSaveRequest request = fixture.request();
        request.setVersion(2L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(SIMULATION_ID, request, USER_ID, null)
        );

        // 회귀 방지: 오래 열린 화면이 더 최신의 상품 선택 결과를 덮어쓰지 못하게 한다.
        assertEquals(SimulationError.SIMULATION_VERSION_CONFLICT, exception.getError());
        Map<?, ?> data = (Map<?, ?>) exception.getData();
        assertEquals(2L, data.get("requestedVersion"));
        assertEquals(3L, data.get("currentVersion"));
    }

    @Test
    @DisplayName("기존 SAVED가 있으면 사용자의 대체 확인 없이 새 결과를 저장하지 않는다")
    void requireConfirmationBeforeReplacingSavedSimulation() {
        Fixture fixture = new Fixture();
        fixture.activeSaved = previousSavedSimulation();

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: 단순 저장 클릭으로 기존 확정 결과가 자동 DRAFT 전환되지 않도록 한다.
        assertEquals(SimulationError.ACTIVE_SAVED_SIMULATION_EXISTS,
                exception.getError());
        assertTrue(fixture.resetSimulationIds.isEmpty());
    }

    @Test
    @DisplayName("사용자가 확인한 기존 SAVED ID가 현재 값과 다르면 대체를 중단한다")
    void rejectReplacementWhenExistingSavedChanged() {
        Fixture fixture = new Fixture();
        fixture.activeSaved = previousSavedSimulation();
        SimulationSaveRequest request = fixture.request();
        request.setReplaceExistingSaved(true);
        request.setExpectedExistingSavedSimulationId(8_999L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(SIMULATION_ID, request, USER_ID, null)
        );

        // 회귀 방지: 확인창을 띄운 뒤 다른 요청이 SAVED를 바꾼 경우 엉뚱한 이력을 대체하지 않는다.
        assertEquals(SimulationError.EXISTING_SAVED_SIMULATION_CHANGED,
                exception.getError());
        assertTrue(fixture.resetSimulationIds.isEmpty());
    }

    @Test
    @DisplayName("다른 시뮬레이션의 포트폴리오는 저장할 수 없다")
    void rejectPortfolioFromAnotherSimulation() {
        Fixture fixture = new Fixture();
        fixture.portfolio.setSimulationId(99_999L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: Path의 simulationId와 무관한 포트폴리오를 주입하지 못하게 한다.
        assertEquals(SimulationError.PORTFOLIO_NOT_IN_SIMULATION,
                exception.getError());
    }

    @Test
    @DisplayName("자동 추천되지 않은 포트폴리오는 최종 저장할 수 없다")
    void rejectNonRecommendedPortfolio() {
        Fixture fixture = new Fixture();
        fixture.portfolio.setRecommended(false);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: 프론트 요청 변조로 비추천 시나리오가 확정되는 것을 막는다.
        assertEquals(SimulationError.PORTFOLIO_NOT_RECOMMENDED,
                exception.getError());
    }

    @Test
    @DisplayName("동일 상품 후보를 중복 선택하면 저장을 거부한다")
    void rejectDuplicateProductSelection() {
        Fixture fixture = new Fixture();
        SimulationSaveRequest request = fixture.request();
        request.setProductSelections(List.of(
                request.getProductSelections().get(0),
                request.getProductSelections().get(0)
        ));

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(SIMULATION_ID, request, USER_ID, null)
        );

        // 회귀 방지: 같은 상품을 두 번 합산해 포트폴리오 금액과 수익이 부풀려지지 않게 한다.
        assertEquals(SimulationError.DUPLICATE_PRODUCT_SELECTION,
                exception.getError());
    }

    @Test
    @DisplayName("프론트 계산식 버전이 서버와 다르면 재조회를 요구한다")
    void rejectDifferentClientFormulaVersion() {
        Fixture fixture = new Fixture();
        SimulationSaveRequest request = fixture.request();
        request.setClientCalculation(clientCalculation("INVESTMENT_V1", 1_030L, 30L));

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(SIMULATION_ID, request, USER_ID, null)
        );

        // 회귀 방지: 서로 다른 계산 공식을 사용한 미리보기와 서버 확정값을 혼용하지 않는다.
        assertEquals(SimulationError.CALCULATION_VERSION_CONFLICT,
                exception.getError());
    }

    @Test
    @DisplayName("프론트 예상값이 달라도 유효한 요청이면 서버 계산값으로 저장하고 차이를 알린다")
    void adjustDifferentClientCalculationToServerValue() {
        Fixture fixture = new Fixture();
        SimulationSaveRequest request = fixture.request();
        request.setClientCalculation(clientCalculation(
                SimulationService.FORMULA_VERSION,
                1_000L,
                0L
        ));

        SimulationSaveResponse response = fixture.service().save(
                SIMULATION_ID, request, USER_ID, null
        );

        // 회귀 방지: 프론트 미리보기 차이를 그대로 확정하지 않고 서버 값을 최종 기준으로 삼는다.
        assertTrue(response.calculationAdjusted());
        assertEquals(1_040L, response.serverCalculation().expectedFutureValue());
        assertEquals(40L, response.clientServerDifference().futureValueDifference());
        assertEquals(40L, response.clientServerDifference().profitDifference());
    }

    @Test
    @DisplayName("낙관적 잠금 갱신이 실패하면 저장 성공으로 응답하지 않는다")
    void rejectSaveWhenOptimisticUpdateLosesRace() {
        Fixture fixture = new Fixture();
        fixture.saveResult = 0;

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: 검증 이후 발생한 동시 저장 충돌도 성공으로 오인하지 않는다.
        assertEquals(SimulationError.SIMULATION_VERSION_CONFLICT,
                exception.getError());
    }

    @Test
    @DisplayName("같은 SAVED 시뮬레이션을 다시 저장하면 다른 이력을 대체하지 않고 선택만 갱신한다")
    void resaveSameSavedSimulation() {
        Fixture fixture = new Fixture();
        fixture.simulation.setStatus(SimulationStatus.SAVED);
        fixture.simulation.setSelectedPortfolioId(PORTFOLIO_ID);
        fixture.simulation.setSavedAt(LocalDateTime.now().minusMinutes(5));
        fixture.simulation.setExpiredAt(null);
        fixture.activeSaved = fixture.simulation;

        SimulationSaveResponse response = fixture.service().save(
                SIMULATION_ID, fixture.request(), USER_ID, null
        );

        // 회귀 방지: 동일 이력의 상품 변경을 다른 SAVED 이력 대체로 잘못 판단하지 않는다.
        assertTrue(!response.replacement().replaced());
        assertTrue(fixture.resetSimulationIds.isEmpty());
        assertEquals(1, fixture.saveCount.get());
    }

    @Test
    @DisplayName("포트폴리오에 필요한 상품 유형을 일부만 선택하면 저장을 거부한다")
    void rejectIncompleteProductTypeSelection() {
        Fixture fixture = new Fixture();
        fixture.portfolio.setDepositAmount(800L);
        fixture.portfolio.setEtfAmount(200L);
        fixture.product.setAllocatedAmount(800L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: ETF 배분이 있는 포트폴리오를 예금 한 종목만으로 확정하지 않는다.
        assertEquals(SimulationError.PRODUCT_TYPE_SELECTION_INCOMPLETE,
                exception.getError());
    }

    @Test
    @DisplayName("ETF에 우대금리 조건을 전달하면 저장을 거부한다")
    void rejectPreferentialConditionForEtf() {
        Fixture fixture = new Fixture();
        fixture.portfolio.setDepositAmount(0L);
        fixture.portfolio.setEtfAmount(1_000L);
        fixture.product.setProductType(ProductType.ETF);
        fixture.detail.setProductType(ProductType.ETF);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: 예적금 전용 우대조건을 ETF 예상 수익률에 더하지 못하게 한다.
        assertEquals(SimulationError.ETF_PREFERENTIAL_CONDITION_NOT_ALLOWED,
                exception.getError());
    }

    @Test
    @DisplayName("선택한 예금 금액이 최소 가입금액보다 작으면 저장을 거부한다")
    void rejectProductBelowMinimumAmount() {
        Fixture fixture = new Fixture();
        fixture.detail.setMinimumAmount(2_000L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID, fixture.request(), USER_ID, null)
        );

        // 회귀 방지: 실제 가입할 수 없는 금액으로 계산된 상품 조합을 확정하지 않는다.
        assertEquals(SimulationError.PRODUCT_LIMIT_EXCEEDED,
                exception.getError());
    }

    @Test
    @DisplayName("실행 단계에서 산정한 첫 적금 계약 한도까지는 최종 저장할 수 있다")
    void saveSavingsAllocationAtFirstContractLimit() {
        Fixture fixture = new Fixture();
        fixture.configureLongTermSavings(36_000_000L);

        SimulationSaveResponse response = fixture.service().save(
                SIMULATION_ID,
                fixture.request(),
                USER_ID,
                null
        );

        // 회귀 방지: 60개월 전체가 아닌 첫 계약 36개월 × 월 100만원을 동일 기준으로 검증한다.
        assertEquals(1, fixture.saveCount.get());
        assertEquals(1_000_000L,
                response.selection().selectedProducts().get(0)
                        .monthlyContributionAmount());
    }

    @Test
    @DisplayName("첫 적금 계약의 월 납입 한도를 초과하면 최종 저장을 거부한다")
    void rejectSavingsAllocationAboveFirstContractLimit() {
        Fixture fixture = new Fixture();
        fixture.configureLongTermSavings(36_000_001L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(
                        SIMULATION_ID,
                        fixture.request(),
                        USER_ID,
                        null
                )
        );

        // 회귀 방지: 재가입 24개월을 신규 원금의 추가 납입 한도로 중복 계산하지 않는다.
        assertEquals(SimulationError.PRODUCT_LIMIT_EXCEEDED,
                exception.getError());
        assertEquals(0, fixture.saveCount.get());
    }

    @Test
    @DisplayName("기존 SAVED의 DRAFT 전환이 실패하면 새 결과 저장도 중단한다")
    void stopReplacementWhenPreviousSavedResetFails() {
        Fixture fixture = new Fixture();
        fixture.activeSaved = previousSavedSimulation();
        fixture.resetResult = 0;
        SimulationSaveRequest request = fixture.request();
        request.setReplaceExistingSaved(true);
        request.setExpectedExistingSavedSimulationId(PREVIOUS_SIMULATION_ID);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().save(SIMULATION_ID, request, USER_ID, null)
        );

        // 회귀 방지: 기존·신규 결과가 동시에 SAVED가 되는 부분 실패 상태를 막는다.
        assertEquals(SimulationError.PREVIOUS_SIMULATION_RESET_FAILED,
                exception.getError());
        assertEquals(0, fixture.saveCount.get());
    }

    private static SimulationSaveRequest.ClientCalculation clientCalculation(
            String formulaVersion,
            long futureValue,
            long profit
    ) {
        SimulationSaveRequest.ClientCalculation calculation =
                new SimulationSaveRequest.ClientCalculation();
        calculation.setFormulaVersion(formulaVersion);
        calculation.setExpectedFutureValue(futureValue);
        calculation.setExpectedProfit(profit);
        return calculation;
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
        private int saveResult = 1;
        private int resetResult = 1;

        private void configureLongTermSavings(long allocatedAmount) {
            simulation.setRequestedAmount(allocatedAmount);
            simulation.setInvestmentPeriodMonths(60);
            simulation.setInvestmentEndDate(simulation.getGiftDate().plusMonths(60));

            portfolio.setDepositAmount(0L);
            portfolio.setSavingsAmount(allocatedAmount);
            portfolio.setEtfAmount(0L);

            result.setInvestmentPrincipal(allocatedAmount);
            tranche.setInvestmentAmount(allocatedAmount);

            product.setProductType(ProductType.SAVINGS);
            product.setAllocatedAmount(allocatedAmount);
            product.setMinimumContractMonths(12);
            product.setMaximumContractMonths(36);

            detail.setProductType(ProductType.SAVINGS);
            detail.setMinimumAmount(null);
            detail.setMaximumAmount(null);
            detail.setMinimumMonths(12);
            detail.setMaximumMonths(36);
            detail.setMonthlyMinimumAmount(10_000L);
            detail.setMonthlyMaximumAmount(1_000_000L);
        }

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
                        case "selectBaseRates" -> List.of(baseRate());
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
                            yield resetResult;
                        }
                        case "markSimulationProductSelected" -> {
                            markSelectedCount.incrementAndGet();
                            yield markSelectedResult;
                        }
                        case "saveSimulation" -> {
                            saveCount.incrementAndGet();
                            yield saveResult;
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
        simulation.setRequestedAmount(1_000L);
        simulation.setStatus(SimulationStatus.DRAFT);
        simulation.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        simulation.setInvestmentPeriodMonths(12);
        simulation.setAsOfDate(LocalDate.of(2026, 8, 5));
        simulation.setGiftDate(LocalDate.of(2026, 8, 5));
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

    private static BaseRateRecord baseRate() {
        BaseRateRecord rate = new BaseRateRecord();
        rate.setBaseInterestRateId(701L);
        rate.setProductVersionId(401L);
        rate.setMinimumMonths(1);
        rate.setMaximumMonths(60);
        rate.setBaseRatePercent(new BigDecimal("3.00"));
        rate.setMaximumRatePercent(new BigDecimal("4.00"));
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
