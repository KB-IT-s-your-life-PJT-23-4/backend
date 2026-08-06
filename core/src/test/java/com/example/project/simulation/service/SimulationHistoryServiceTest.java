package com.example.project.simulation.service;

import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.TaxPaymentMethod;
import com.example.project.simulation.dto.response.SimulationHistoryResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.mapper.SimulationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationHistoryServiceTest {

    @Test
    @DisplayName("상태 필터의 앞뒤 공백을 제거하고 대문자로 변환한다")
    void normalizeStatusFilter() {
        HistoryMapperStub mapperStub = new HistoryMapperStub();
        SimulationHistoryService service = service(mapperStub);

        SimulationHistoryResponse response = service.getHistory(
                1L,
                "  saved  ",
                null,
                null,
                null
        );

        assertEquals(SimulationStatus.SAVED, mapperStub.observedStatus);
        assertTrue(response.items().isEmpty());
        assertEquals(0, response.pagination().getPage());
        assertEquals(10, response.pagination().getSize());
    }

    @Test
    @DisplayName("빈 상태 필터는 전체 조회로 처리한다")
    void treatBlankStatusAsAll() {
        HistoryMapperStub mapperStub = new HistoryMapperStub();
        SimulationHistoryService service = service(mapperStub);

        service.getHistory(1L, "   ", null, 0, 10);

        assertNull(mapperStub.observedStatus);
    }

    @Test
    @DisplayName("DRAFT와 SAVED 이력을 추천 수익률 범위 및 선택 결과와 함께 반환한다")
    void returnDraftAndSavedHistorySummaries() {
        HistoryMapperStub mapperStub = new HistoryMapperStub();
        SimulationRecord draft = simulation(
                100L,
                SimulationStatus.DRAFT,
                null,
                1_000L
        );
        SimulationRecord saved = simulation(
                200L,
                SimulationStatus.SAVED,
                2_999L,
                2_000L
        );
        mapperStub.totalElements = 2L;
        mapperStub.simulations = List.of(draft, saved);
        mapperStub.results = List.of(
                result(1_001L, 100L, 1_000L, 0L),
                result(2_001L, 200L, 2_000L, 100L)
        );
        mapperStub.recommendations = List.of(
                portfolio(1_101L, 1_001L, 100L, RiskProfile.CONSERVATIVE, 1_050L),
                portfolio(1_102L, 1_001L, 100L, RiskProfile.BALANCED, 1_100L),
                portfolio(1_103L, 1_001L, 100L, RiskProfile.AGGRESSIVE, 1_200L),
                portfolio(2_101L, 2_001L, 200L, RiskProfile.CONSERVATIVE, 2_100L),
                portfolio(2_102L, 2_001L, 200L, RiskProfile.BALANCED, 2_200L),
                portfolio(2_103L, 2_001L, 200L, RiskProfile.AGGRESSIVE, 2_400L)
        );
        mapperStub.selectedPortfolios = List.of(
                portfolio(2_999L, 2_001L, 200L, RiskProfile.BALANCED, 2_300L)
        );
        mapperStub.selectedProducts = List.of(
                product(2_999L, ProductType.DEPOSIT, 1_200L, 1_300L),
                product(2_999L, ProductType.ETF, 800L, 1_000L)
        );

        SimulationHistoryResponse response = service(mapperStub).getHistory(
                1L,
                null,
                null,
                0,
                10
        );

        assertEquals(2, response.items().size());
        assertEquals(2L, response.pagination().getTotalElements());
        assertTrue(response.pagination().isFirst());
        assertTrue(response.pagination().isLast());

        SimulationHistoryResponse.Item draftItem = response.items().get(0);
        assertEquals(SimulationStatus.DRAFT, draftItem.status());
        assertNull(draftItem.selection());
        assertEquals(
                RiskProfile.CONSERVATIVE,
                draftItem.expectedReturnRange().minimum().portfolioType()
        );
        assertEquals(
                new BigDecimal("5.00"),
                draftItem.expectedReturnRange().minimum().expectedReturnRatePercent()
        );
        assertEquals(
                RiskProfile.AGGRESSIVE,
                draftItem.expectedReturnRange().maximum().portfolioType()
        );
        assertEquals(
                new BigDecimal("20.00"),
                draftItem.expectedReturnRange().maximum().expectedReturnRatePercent()
        );

        SimulationHistoryResponse.Item savedItem = response.items().get(1);
        assertEquals(SimulationStatus.SAVED, savedItem.status());
        assertEquals(2_999L, savedItem.selection().selectedPortfolioId());
        assertEquals(2_300L, savedItem.selection().expectedFutureValue());
        assertEquals(300L, savedItem.selection().expectedProfit());
        assertEquals(
                new BigDecimal("15.00"),
                savedItem.selection().expectedReturnRatePercent()
        );
        assertEquals(
                List.of(ProductType.DEPOSIT, ProductType.ETF),
                savedItem.selection().selectedProductTypes()
        );
    }

    @Test
    @DisplayName("투자 성향별 추천 포트폴리오가 누락되면 이력 조회를 중단한다")
    void rejectIncompleteRecommendations() {
        HistoryMapperStub mapperStub = new HistoryMapperStub();
        mapperStub.totalElements = 1L;
        mapperStub.simulations = List.of(
                simulation(100L, SimulationStatus.DRAFT, null, 1_000L)
        );
        mapperStub.results = List.of(result(1_001L, 100L, 1_000L, 0L));
        mapperStub.recommendations = List.of(
                portfolio(1_101L, 1_001L, 100L, RiskProfile.CONSERVATIVE, 1_050L),
                portfolio(1_102L, 1_001L, 100L, RiskProfile.BALANCED, 1_100L)
        );

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> service(mapperStub).getHistory(1L, null, null, 0, 10)
        );

        assertEquals(
                SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE,
                exception.getError()
        );
    }

    private SimulationHistoryService service(HistoryMapperStub mapperStub) {
        return new SimulationHistoryService(
                mapperStub.mapper(),
                new UserValidatedSimulationService()
        );
    }

    private SimulationRecord simulation(
            Long simulationId,
            SimulationStatus status,
            Long selectedPortfolioId,
            Long requestedAmount
    ) {
        SimulationRecord simulation = new SimulationRecord();
        simulation.setSimulationId(simulationId);
        simulation.setFamilyId(31L);
        simulation.setFamilyName("김민준");
        simulation.setRelation("LINEAL_DESCENDANT");
        simulation.setRequestedAmount(requestedAmount);
        simulation.setStatus(status);
        simulation.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        simulation.setInvestmentPeriodMonths(36);
        simulation.setAsOfDate(LocalDate.of(2026, 8, 6));
        simulation.setInvestmentEndDate(LocalDate.of(2029, 8, 6));
        simulation.setSelectedPortfolioId(selectedPortfolioId);
        simulation.setVersion(1L);
        simulation.setCreatedAt(LocalDateTime.of(2026, 8, 6, 10, 0));
        simulation.setUpdatedAt(LocalDateTime.of(2026, 8, 6, 10, 30));
        simulation.setSavedAt(
                status == SimulationStatus.SAVED
                        ? LocalDateTime.of(2026, 8, 6, 10, 30)
                        : null
        );
        simulation.setExpiredAt(
                status == SimulationStatus.DRAFT
                        ? LocalDateTime.of(2026, 9, 6, 10, 0)
                        : null
        );
        return simulation;
    }

    private SimulationResultRecord result(
            Long resultId,
            Long simulationId,
            Long investmentPrincipal,
            Long giftTax
    ) {
        SimulationResultRecord result = new SimulationResultRecord();
        result.setResultId(resultId);
        result.setSimulationId(simulationId);
        result.setScenarioType(ScenarioType.IMMEDIATE);
        result.setGiftTax(giftTax);
        result.setInvestmentPrincipal(investmentPrincipal);
        return result;
    }

    private SimulationPortfolioRecord portfolio(
            Long portfolioId,
            Long resultId,
            Long simulationId,
            RiskProfile portfolioType,
            Long expectedFutureValue
    ) {
        SimulationPortfolioRecord portfolio = new SimulationPortfolioRecord();
        portfolio.setPortfolioId(portfolioId);
        portfolio.setResultId(resultId);
        portfolio.setSimulationId(simulationId);
        portfolio.setScenarioType(ScenarioType.IMMEDIATE);
        portfolio.setPortfolioType(portfolioType);
        portfolio.setExpectedFutureValue(expectedFutureValue);
        portfolio.setRecommended(true);
        return portfolio;
    }

    private SimulationProductRecord product(
            Long portfolioId,
            ProductType productType,
            Long allocatedAmount,
            Long expectedFutureValue
    ) {
        SimulationProductRecord product = new SimulationProductRecord();
        product.setPortfolioId(portfolioId);
        product.setProductType(productType);
        product.setSelected(true);
        product.setAllocatedAmount(allocatedAmount);
        product.setExpectedFutureValue(expectedFutureValue);
        return product;
    }

    private static final class UserValidatedSimulationService extends SimulationService {

        private UserValidatedSimulationService() {
            super(null, null, null, null);
        }

        @Override
        void validateUser(Long userId) {
            // History behavior is tested independently from user lookup.
        }
    }

    private static final class HistoryMapperStub implements InvocationHandler {

        private long totalElements;
        private List<SimulationRecord> simulations = List.of();
        private List<SimulationResultRecord> results = List.of();
        private List<SimulationPortfolioRecord> recommendations = List.of();
        private List<SimulationPortfolioRecord> selectedPortfolios = List.of();
        private List<SimulationProductRecord> selectedProducts = List.of();
        private SimulationStatus observedStatus;

        private SimulationMapper mapper() {
            return (SimulationMapper) Proxy.newProxyInstance(
                    SimulationMapper.class.getClassLoader(),
                    new Class<?>[]{SimulationMapper.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "countSimulations" -> {
                    observedStatus = (SimulationStatus) arguments[1];
                    yield totalElements;
                }
                case "selectSimulationPage" -> simulations;
                case "selectResultsBySimulationIds" -> results;
                case "selectRecommendedPortfoliosBySimulationIds" -> recommendations;
                case "selectPortfoliosByIds" -> selectedPortfolios;
                case "selectSelectedProductsByPortfolioIds" -> selectedProducts;
                case "toString" -> "HistoryMapperStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            return 0D;
        }
    }
}
