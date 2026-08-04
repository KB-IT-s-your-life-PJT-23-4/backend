package com.example.project.simulation.service;

import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.dto.response.SimulationHistoryResponse;
import com.example.project.simulation.dto.response.SimulationResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class SimulationHistoryService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

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
            if (page < 0 || size < 1 || size > MAX_SIZE) {
                throw new SimulationException(
                        SimulationError.INVALID_PAGE_REQUEST);
            }
            if (familyId != null) {
                if (familyId <= 0) {
                    throw new SimulationException(SimulationError.INVALID_FAMILY_ID);
                }
                var family = simulationMapper.selectFamily(familyId);
                if (family == null) {
                    throw new SimulationException(SimulationError.FAMILY_NOT_FOUND);
                }
                if (!Objects.equals(family.getUserId(), userId)) {
                    throw new SimulationException(
                            SimulationError.FAMILY_ACCESS_DENIED);
                }
            }

            LocalDateTime now = LocalDateTime.now();
            long total = simulationMapper.countSimulations(
                    userId,
                    status,
                    familyId,
                    now
            );
            List<SimulationRecord> simulations = simulationMapper.selectSimulationPage(
                    userId,
                    status,
                    familyId,
                    now,
                    (long) page * size,
                    size
            );
            List<SimulationHistoryResponse.Item> items =
                    (simulations == null ? List.<SimulationRecord>of() : simulations)
                            .stream()
                            .map(simulationService::buildResponse)
                            .map(this::toItem)
                            .toList();
            int totalPages = total == 0 ? 0
                    : (int) Math.ceil(total / (double) size);
            boolean first = page == 0;
            boolean last = totalPages == 0 || page >= totalPages - 1;
            return new SimulationHistoryResponse(
                    items,
                    new SimulationHistoryResponse.Pagination(
                            page,
                            size,
                            total,
                            totalPages,
                            items.size(),
                            first,
                            last,
                            !last,
                            !first
                    )
            );
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Simulation history read failed", exception);
            throw new SimulationException(
                    SimulationError.SIMULATION_HISTORY_READ_FAILED,
                    exception
            );
        }
    }

    private SimulationHistoryResponse.Item toItem(SimulationResponse response) {
        Map<Long, SimulationResponse.Result> resultById = response.results().stream()
                .collect(Collectors.toMap(
                        SimulationResponse.Result::resultId,
                        Function.identity()
                ));
        Map<Long, SimulationResponse.Portfolio> portfolioById =
                response.results().stream()
                        .flatMap(result -> result.portfolios().stream())
                        .collect(Collectors.toMap(
                                SimulationResponse.Portfolio::portfolioId,
                                Function.identity()
                        ));
        List<SimulationHistoryResponse.ReturnPoint> points =
                response.recommendations().stream()
                        .map(recommendation -> {
                            SimulationResponse.Result result =
                                    resultById.get(recommendation.resultId());
                            SimulationResponse.Portfolio portfolio =
                                    portfolioById.get(recommendation.portfolioId());
                            if (result == null || portfolio == null
                                    || result.investmentPrincipal() == null
                                    || result.investmentPrincipal() <= 0) {
                                throw new SimulationException(
                                        SimulationError.SIMULATION_RETURN_CALCULATION_INVALID);
                            }
                            long profit = portfolio.expectedFutureValue()
                                    - result.investmentPrincipal();
                            return new SimulationHistoryResponse.ReturnPoint(
                                    recommendation.portfolioType(),
                                    recommendation.scenarioType(),
                                    result.investmentPrincipal(),
                                    portfolio.expectedFutureValue(),
                                    profit,
                                    returnRate(profit, result.investmentPrincipal())
                            );
                        })
                        .toList();
        if (points.size() != 3) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RECOMMENDATION_INCOMPLETE);
        }
        Comparator<SimulationHistoryResponse.ReturnPoint> comparator =
                Comparator.comparing(
                                SimulationHistoryResponse.ReturnPoint
                                        ::expectedReturnRatePercent
                        )
                        .thenComparing(point -> point.portfolioType().ordinal());
        SimulationHistoryResponse.ReturnPoint minimum =
                points.stream().min(comparator).orElseThrow();
        SimulationHistoryResponse.ReturnPoint maximum =
                points.stream().max(comparator).orElseThrow();

        SimulationHistoryResponse.SelectionSummary selection = null;
        if (response.selection() != null) {
            SimulationResponse.Selection selected = response.selection();
            long profit = selected.expectedFutureValue()
                    - selected.investmentPrincipal();
            List<ProductType> productTypes = selected.selectedProducts().stream()
                    .map(SimulationResponse.Product::productType)
                    .distinct()
                    .toList();
            selection = new SimulationHistoryResponse.SelectionSummary(
                    selected.selectedPortfolioId(),
                    selected.portfolioType(),
                    selected.resultId(),
                    selected.scenarioType(),
                    selected.estimatedGiftTax(),
                    selected.investmentPrincipal(),
                    selected.expectedFutureValue(),
                    profit,
                    returnRate(profit, selected.investmentPrincipal()),
                    productTypes
            );
        }

        return new SimulationHistoryResponse.Item(
                response.simulationId(),
                response.status(),
                response.version(),
                new SimulationHistoryResponse.Family(
                        response.family().familyId(),
                        response.family().recipientName(),
                        response.family().relation()
                ),
                new SimulationHistoryResponse.InputSummary(
                        response.input().requestedAmount(),
                        response.input().taxPaymentMethod(),
                        response.input().investmentPeriodMonths(),
                        response.input().asOfDate(),
                        response.input().investmentEndDate()
                ),
                new SimulationHistoryResponse.ExpectedReturnRange(
                        "RECOMMENDED_PORTFOLIOS",
                        response.input().investmentPeriodMonths(),
                        minimum,
                        maximum
                ),
                selection,
                response.createdAt(),
                response.updatedAt(),
                response.savedAt(),
                response.expiresAt()
        );
    }

    private SimulationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SimulationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new SimulationException(
                    SimulationError.INVALID_SIMULATION_STATUS_FILTER);
        }
    }

    private BigDecimal returnRate(long profit, long principal) {
        if (principal <= 0) {
            throw new SimulationException(
                    SimulationError.SIMULATION_RETURN_CALCULATION_INVALID);
        }
        return BigDecimal.valueOf(profit)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(principal), 2, RoundingMode.HALF_UP);
    }
}
