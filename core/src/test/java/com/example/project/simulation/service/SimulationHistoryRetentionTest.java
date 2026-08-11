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
import com.example.project.simulation.mapper.SimulationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulationHistoryRetentionTest {

    private static final long SIMULATION_ID = 100L;
    private static final long RESULT_ID = 200L;
    private static final long SELECTED_PORTFOLIO_ID = 301L;

    @Test
    @DisplayName("이전에 저장된 DRAFT 이력은 선택 세금과 기존 시각을 유지한다")
    void returnSelectionForPreviouslySavedDraft() {
        SimulationRecord simulation = simulation();
        SimulationResultRecord result = result();
        List<SimulationPortfolioRecord> recommendations = List.of(
                portfolio(SELECTED_PORTFOLIO_ID, RiskProfile.CONSERVATIVE, 1_100L),
                portfolio(302L, RiskProfile.BALANCED, 1_200L),
                portfolio(303L, RiskProfile.AGGRESSIVE, 1_300L)
        );
        SimulationProductRecord selectedProduct = selectedProduct();

        SimulationMapper mapper = (SimulationMapper) Proxy.newProxyInstance(
                SimulationMapper.class.getClassLoader(),
                new Class<?>[]{SimulationMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countSimulations" -> 1L;
                    case "selectSimulationPage" -> List.of(simulation);
                    case "selectResultsBySimulationIds" -> List.of(result);
                    case "selectRecommendedPortfoliosBySimulationIds" -> recommendations;
                    case "selectPortfoliosByIds" -> List.of(recommendations.get(0));
                    case "selectSelectedProductsByPortfolioIds" ->
                            List.of(selectedProduct);
                    case "toString" -> "SimulationHistoryRetentionMapper";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
        SimulationHistoryService service = new SimulationHistoryService(
                mapper,
                new UserValidatedSimulationService()
        );

        SimulationHistoryResponse response = service.getHistory(
                7L,
                null,
                null,
                0,
                10
        );

        SimulationHistoryResponse.Item item = response.items().get(0);
        assertEquals(SimulationStatus.DRAFT, item.status());
        assertNotNull(item.selection());
        assertEquals(12_345L, item.selection().estimatedGiftTax());
        assertEquals(SELECTED_PORTFOLIO_ID, item.selection().selectedPortfolioId());
        assertEquals(simulation.getCreatedAt(), item.createdAt());
        assertEquals(simulation.getUpdatedAt(), item.updatedAt());
        assertNull(item.savedAt());
    }

    private static SimulationRecord simulation() {
        SimulationRecord simulation = new SimulationRecord();
        simulation.setSimulationId(SIMULATION_ID);
        simulation.setFamilyId(31L);
        simulation.setFamilyName("김하늘");
        simulation.setRelation("LINEAL_DESCENDANT");
        simulation.setRequestedAmount(400_000_000L);
        simulation.setStatus(SimulationStatus.DRAFT);
        simulation.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        simulation.setInvestmentPeriodMonths(36);
        simulation.setAsOfDate(LocalDate.of(2026, 8, 1));
        simulation.setGiftDate(LocalDate.of(2026, 8, 1));
        simulation.setInvestmentEndDate(LocalDate.of(2029, 8, 1));
        simulation.setSelectedPortfolioId(SELECTED_PORTFOLIO_ID);
        simulation.setVersion(2L);
        simulation.setCreatedAt(LocalDateTime.of(2026, 8, 1, 9, 30));
        simulation.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 9, 35));
        simulation.setSavedAt(null);
        simulation.setExpiredAt(LocalDateTime.of(2026, 9, 6, 13, 15));
        return simulation;
    }

    private static SimulationResultRecord result() {
        SimulationResultRecord result = new SimulationResultRecord();
        result.setResultId(RESULT_ID);
        result.setSimulationId(SIMULATION_ID);
        result.setScenarioType(ScenarioType.IMMEDIATE);
        result.setGiftTax(12_345L);
        result.setInvestmentPrincipal(1_000L);
        return result;
    }

    private static SimulationPortfolioRecord portfolio(
            long portfolioId,
            RiskProfile riskProfile,
            long futureValue
    ) {
        SimulationPortfolioRecord portfolio = new SimulationPortfolioRecord();
        portfolio.setPortfolioId(portfolioId);
        portfolio.setResultId(RESULT_ID);
        portfolio.setSimulationId(SIMULATION_ID);
        portfolio.setScenarioType(ScenarioType.IMMEDIATE);
        portfolio.setPortfolioType(riskProfile);
        portfolio.setExpectedFutureValue(futureValue);
        portfolio.setRecommended(true);
        return portfolio;
    }

    private static SimulationProductRecord selectedProduct() {
        SimulationProductRecord product = new SimulationProductRecord();
        product.setPortfolioId(SELECTED_PORTFOLIO_ID);
        product.setProductType(ProductType.DEPOSIT);
        product.setSelected(true);
        product.setAllocatedAmount(1_000L);
        product.setExpectedFutureValue(1_100L);
        return product;
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

    private static final class UserValidatedSimulationService extends SimulationService {

        private UserValidatedSimulationService() {
            super(null, null, null, null, null);
        }

        @Override
        void validateUser(Long userId) {
            // History retention is tested independently from user lookup.
        }
    }
}
