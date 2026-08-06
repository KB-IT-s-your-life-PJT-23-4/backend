package com.example.project.simulation.service;

import com.example.project.common.api.Pagination;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.dto.response.SimulationHistoryResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.mapper.SimulationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class SimulationHistoryService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final String RETURN_RANGE_BASIS = "RECOMMENDED_PORTFOLIOS";

    private final SimulationMapper simulationMapper;
    private final SimulationService simulationService;

    @Transactional(readOnly = true)
    public SimulationHistoryResponse getHistory(
            Long userId,
            String statusValue,
            Long familyId,
            Integer pageValue,
            Integer sizeValue
    ) {
        try {
            simulationService.validateUser(userId);
            SimulationStatus status = parseStatus(statusValue);
            int page = pageValue == null ? 0 : pageValue;
            int size = sizeValue == null ? DEFAULT_SIZE : sizeValue;
            validateRequest(userId, familyId, page, size);

            LocalDateTime now = LocalDateTime.now();
            long totalElements = simulationMapper.countSimulations(
                    userId,
                    status,
                    familyId,
                    now
            );
            List<SimulationRecord> simulations = safeList(
                    simulationMapper.selectSimulationPage(
                            userId,
                            status,
                            familyId,
                            now,
                            (long) page * size,
                            size
                    )
            );
            if (simulations.isEmpty()) {
                return new SimulationHistoryResponse(
                        List.of(),
                        pagination(page, size, totalElements, 0)
                );
            }

            List<Long> simulationIds = simulations.stream()
                    .map(SimulationRecord::getSimulationId)
                    .toList();
            List<SimulationResultRecord> results = safeList(
                    simulationMapper.selectResultsBySimulationIds(simulationIds)
            );
            List<SimulationPortfolioRecord> recommendations = safeList(
                    simulationMapper.selectRecommendedPortfoliosBySimulationIds(
                            simulationIds
                    )
            );

            Map<Long, SimulationResultRecord> resultById = results.stream()
                    .collect(Collectors.toMap(
                            SimulationResultRecord::getResultId,
                            Function.identity(),
                            (first, duplicate) -> {
                                throw new SimulationException(
                                        SimulationError.SIMULATION_HISTORY_INCOMPLETE
                                );
                            }
                    ));
            Map<Long, List<SimulationPortfolioRecord>> recommendationsBySimulation =
                    recommendations.stream().collect(Collectors.groupingBy(
                            SimulationPortfolioRecord::getSimulationId
                    ));
            Map<Long, SimulationHistoryResponse.SelectionSummary> selections =
                    loadSelections(simulations, resultById);

            List<SimulationHistoryResponse.Item> items = simulations.stream()
                    .map(simulation -> toItem(
                            simulation,
                            recommendationsBySimulation.getOrDefault(
                                    simulation.getSimulationId(),
                                    List.of()
                            ),
                            resultById,
                            selections.get(simulation.getSimulationId())
                    ))
                    .toList();

            return new SimulationHistoryResponse(
                    items,
                    pagination(page, size, totalElements, items.size())
            );
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Simulation history read failed. userId={}", userId, exception);
            throw new SimulationException(
                    SimulationError.SIMULATION_HISTORY_READ_FAILED,
                    exception
            );
        }
    }

    private Map<Long, SimulationHistoryResponse.SelectionSummary> loadSelections(
            List<SimulationRecord> simulations,
            Map<Long, SimulationResultRecord> resultById
    ) {
        List<SimulationRecord> selectedSimulations = simulations.stream()
                .filter(simulation -> simulation.getSelectedPortfolioId() != null)
                .toList();
        if (selectedSimulations.isEmpty()) {
            return Map.of();
        }

        List<Long> selectedPortfolioIds = selectedSimulations.stream()
                .map(SimulationRecord::getSelectedPortfolioId)
                .distinct()
                .toList();
        if (selectedPortfolioIds.size() != selectedSimulations.size()) {
            throw new SimulationException(SimulationError.SIMULATION_HISTORY_INCOMPLETE);
        }

        Map<Long, SimulationPortfolioRecord> portfolioById = safeList(
                simulationMapper.selectPortfoliosByIds(selectedPortfolioIds)
        ).stream().collect(Collectors.toMap(
                SimulationPortfolioRecord::getPortfolioId,
                Function.identity(),
                (first, duplicate) -> {
                    throw new SimulationException(
                            SimulationError.SIMULATION_HISTORY_INCOMPLETE
                    );
                }
        ));
        Map<Long, List<SimulationProductRecord>> productsByPortfolio = safeList(
                simulationMapper.selectSelectedProductsByPortfolioIds(
                        selectedPortfolioIds
                )
        ).stream().collect(Collectors.groupingBy(
                SimulationProductRecord::getPortfolioId
        ));

        Map<Long, SimulationHistoryResponse.SelectionSummary> selections = new HashMap<>();
        for (SimulationRecord simulation : selectedSimulations) {
            SimulationPortfolioRecord portfolio = portfolioById.get(
                    simulation.getSelectedPortfolioId()
            );
            if (portfolio == null) {
                throw new SimulationException(
                        SimulationError.SIMULATION_HISTORY_INCOMPLETE
                );
            }
            if (!Objects.equals(
                    simulation.getSimulationId(),
                    portfolio.getSimulationId()
            )) {
                throw new SimulationException(
                        SimulationError.SELECTED_PORTFOLIO_MISMATCH
                );
            }

            SimulationResultRecord result = resultById.get(portfolio.getResultId());
            List<SimulationProductRecord> products = productsByPortfolio.getOrDefault(
                    portfolio.getPortfolioId(),
                    List.of()
            );
            validateSelection(portfolio, result, products);

            long expectedFutureValue = sumExpectedFutureValue(products);
            long expectedProfit = subtractMoney(
                    expectedFutureValue,
                    result.getInvestmentPrincipal()
            );
            Set<ProductType> productTypes = new LinkedHashSet<>();
            for (SimulationProductRecord product : products) {
                if (product.getProductType() == null) {
                    throw new SimulationException(
                            SimulationError.SIMULATION_HISTORY_INCOMPLETE
                    );
                }
                productTypes.add(product.getProductType());
            }

            selections.put(
                    simulation.getSimulationId(),
                    new SimulationHistoryResponse.SelectionSummary(
                            portfolio.getPortfolioId(),
                            portfolio.getPortfolioType(),
                            result.getResultId(),
                            result.getScenarioType(),
                            result.getGiftTax(),
                            result.getInvestmentPrincipal(),
                            expectedFutureValue,
                            expectedProfit,
                            returnRate(expectedProfit, result.getInvestmentPrincipal()),
                            List.copyOf(productTypes)
                    )
            );
        }
        return selections;
    }

    private SimulationHistoryResponse.Item toItem(
            SimulationRecord simulation,
            List<SimulationPortfolioRecord> recommendations,
            Map<Long, SimulationResultRecord> resultById,
            SimulationHistoryResponse.SelectionSummary selection
    ) {
        SimulationHistoryResponse.ExpectedReturnRange returnRange =
                expectedReturnRange(
                        simulation.getInvestmentPeriodMonths(),
                        recommendations,
                        resultById
                );
        if (simulation.getStatus() == SimulationStatus.SAVED && selection == null) {
            throw new SimulationException(SimulationError.SIMULATION_HISTORY_INCOMPLETE);
        }

        return new SimulationHistoryResponse.Item(
                simulation.getSimulationId(),
                simulation.getStatus(),
                simulation.getVersion(),
                new SimulationHistoryResponse.Family(
                        simulation.getFamilyId(),
                        simulation.getFamilyName(),
                        simulation.getRelation()
                ),
                new SimulationHistoryResponse.InputSummary(
                        simulation.getRequestedAmount(),
                        simulation.getTaxPaymentMethod(),
                        simulation.getInvestmentPeriodMonths(),
                        simulation.getAsOfDate(),
                        simulation.getInvestmentEndDate()
                ),
                returnRange,
                selection,
                simulation.getCreatedAt(),
                simulation.getUpdatedAt(),
                simulation.getSavedAt(),
                simulation.getExpiredAt()
        );
    }

    private SimulationHistoryResponse.ExpectedReturnRange expectedReturnRange(
            Integer investmentPeriodMonths,
            List<SimulationPortfolioRecord> recommendations,
            Map<Long, SimulationResultRecord> resultById
    ) {
        Map<RiskProfile, List<SimulationPortfolioRecord>> byPortfolioType =
                recommendations.stream()
                        .filter(portfolio -> portfolio.getPortfolioType() != null)
                        .collect(Collectors.groupingBy(
                                SimulationPortfolioRecord::getPortfolioType,
                                () -> new EnumMap<>(RiskProfile.class),
                                Collectors.toList()
                        ));
        boolean complete = recommendations.size() == RiskProfile.values().length;
        for (RiskProfile type : RiskProfile.values()) {
            complete &= byPortfolioType.getOrDefault(type, List.of()).size() == 1;
        }
        if (!complete) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE
            );
        }

        List<SimulationHistoryResponse.ReturnPoint> points = new ArrayList<>();
        for (RiskProfile type : RiskProfile.values()) {
            SimulationPortfolioRecord portfolio = byPortfolioType.get(type).get(0);
            SimulationResultRecord result = resultById.get(portfolio.getResultId());
            if (result == null
                    || result.getScenarioType() == null
                    || portfolio.getExpectedFutureValue() == null) {
                throw new SimulationException(
                        SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE
                );
            }
            long expectedProfit = subtractMoney(
                    portfolio.getExpectedFutureValue(),
                    result.getInvestmentPrincipal()
            );
            points.add(new SimulationHistoryResponse.ReturnPoint(
                    portfolio.getPortfolioType(),
                    result.getScenarioType(),
                    result.getInvestmentPrincipal(),
                    portfolio.getExpectedFutureValue(),
                    expectedProfit,
                    returnRate(expectedProfit, result.getInvestmentPrincipal())
            ));
        }

        Comparator<SimulationHistoryResponse.ReturnPoint> ascending =
                Comparator.comparing(
                                SimulationHistoryResponse.ReturnPoint
                                        ::expectedReturnRatePercent
                        )
                        .thenComparingInt(point -> point.portfolioType().ordinal());
        SimulationHistoryResponse.ReturnPoint minimum = points.stream()
                .min(ascending)
                .orElseThrow();
        SimulationHistoryResponse.ReturnPoint maximum = points.stream()
                .sorted(
                        Comparator.comparing(
                                        SimulationHistoryResponse.ReturnPoint
                                                ::expectedReturnRatePercent,
                                        Comparator.reverseOrder()
                                )
                                .thenComparingInt(
                                        point -> point.portfolioType().ordinal()
                                )
                )
                .findFirst()
                .orElseThrow();

        return new SimulationHistoryResponse.ExpectedReturnRange(
                RETURN_RANGE_BASIS,
                investmentPeriodMonths,
                minimum,
                maximum
        );
    }

    private void validateRequest(Long userId, Long familyId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new SimulationException(SimulationError.INVALID_PAGE_REQUEST);
        }
        if (familyId == null) {
            return;
        }
        if (familyId <= 0) {
            throw new SimulationException(SimulationError.INVALID_FAMILY_ID);
        }
        var family = simulationMapper.selectFamily(familyId);
        if (family == null) {
            throw new SimulationException(SimulationError.FAMILY_NOT_FOUND);
        }
        if (!Objects.equals(family.getUserId(), userId)) {
            throw new SimulationException(SimulationError.FAMILY_ACCESS_DENIED);
        }
    }

    private void validateSelection(
            SimulationPortfolioRecord portfolio,
            SimulationResultRecord result,
            List<SimulationProductRecord> products
    ) {
        if (portfolio.getPortfolioType() == null
                || result == null
                || result.getScenarioType() == null
                || result.getGiftTax() == null
                || result.getInvestmentPrincipal() == null
                || products.isEmpty()) {
            throw new SimulationException(SimulationError.SIMULATION_HISTORY_INCOMPLETE);
        }
        long allocatedAmount = sumAllocatedAmount(products);
        if (allocatedAmount != result.getInvestmentPrincipal()) {
            throw new SimulationException(SimulationError.SIMULATION_HISTORY_INCOMPLETE);
        }
    }

    private SimulationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SimulationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new SimulationException(
                    SimulationError.INVALID_SIMULATION_STATUS_FILTER
            );
        }
    }

    private Pagination pagination(
            int page,
            int size,
            long totalElements,
            int numberOfElements
    ) {
        long pages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        int totalPages = pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
        boolean hasNext = page + 1L < pages;
        return new Pagination(
                page,
                size,
                totalElements,
                totalPages,
                numberOfElements,
                page == 0,
                !hasNext,
                hasNext,
                page > 0
        );
    }

    private BigDecimal returnRate(long expectedProfit, Long investmentPrincipal) {
        if (investmentPrincipal == null || investmentPrincipal <= 0) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RETURN_CALCULATION_INVALID
            );
        }
        return BigDecimal.valueOf(expectedProfit)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(investmentPrincipal), 2, RoundingMode.HALF_UP);
    }

    private long subtractMoney(Long minuend, Long subtrahend) {
        if (minuend == null || subtrahend == null) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RETURN_CALCULATION_INVALID
            );
        }
        try {
            return Math.subtractExact(minuend, subtrahend);
        } catch (ArithmeticException exception) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RETURN_CALCULATION_INVALID,
                    exception
            );
        }
    }

    private long sumAllocatedAmount(List<SimulationProductRecord> products) {
        try {
            long total = 0;
            for (SimulationProductRecord product : products) {
                total = Math.addExact(total, product.getAllocatedAmount());
            }
            return total;
        } catch (NullPointerException | ArithmeticException exception) {
            throw new SimulationException(
                    SimulationError.SIMULATION_HISTORY_INCOMPLETE,
                    exception
            );
        }
    }

    private long sumExpectedFutureValue(List<SimulationProductRecord> products) {
        try {
            long total = 0;
            for (SimulationProductRecord product : products) {
                total = Math.addExact(total, product.getExpectedFutureValue());
            }
            return total;
        } catch (NullPointerException | ArithmeticException exception) {
            throw new SimulationException(
                    SimulationError.SIMULATION_HISTORY_INCOMPLETE,
                    exception
            );
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
