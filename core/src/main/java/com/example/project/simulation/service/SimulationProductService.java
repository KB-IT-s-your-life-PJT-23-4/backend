package com.example.project.simulation.service;

import com.example.project.simulation.domain.BaseRateRecord;
import com.example.project.simulation.domain.EtfHoldingRecord;
import com.example.project.simulation.domain.PreferentialRateRecord;
import com.example.project.simulation.domain.ProductDataVersionRecord;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.ProductVersionDetailRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.dto.response.ProductDetailResponse;
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
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Log4j2
public class SimulationProductService {

    private static final int MAX_HOLDINGS = 10;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final SimulationMapper simulationMapper;
    private final SimulationService simulationService;

    @Transactional(readOnly = true)
    public ProductDetailResponse getDetail(
            Long simulationId,
            Long productVersionId,
            Long userId
    ) {
        try {
            simulationService.validateUser(userId);
            if (simulationId == null || simulationId <= 0) {
                throw new SimulationException(SimulationError.INVALID_SIMULATION_ID);
            }
            if (productVersionId == null || productVersionId <= 0) {
                throw new SimulationException(
                        SimulationError.INVALID_PRODUCT_VERSION_ID);
            }
            SimulationRecord simulation =
                    simulationService.requireSimulation(simulationId, userId);
            ProductVersionDetailRecord product =
                    simulationMapper.selectProductVersionDetail(productVersionId);
            if (product == null) {
                throw new SimulationException(
                        SimulationError.PRODUCT_VERSION_NOT_FOUND);
            }
            if (!Objects.equals(
                    product.getProductDataVersionId(),
                    simulation.getProductDataVersionId()
            )) {
                throw new SimulationException(
                        SimulationError.PRODUCT_DATA_VERSION_MISMATCH);
            }
            if (!simulationMapper.existsProductInSimulation(
                    simulationId,
                    productVersionId
            )) {
                throw new SimulationException(
                        SimulationError.PRODUCT_NOT_IN_SIMULATION);
            }

            ProductDataVersionRecord dataVersion =
                    simulationMapper.selectProductDataVersion(
                            simulation.getProductDataVersionId()
                    );
            if (dataVersion == null) {
                throw new SimulationException(
                        SimulationError.PRODUCT_DETAIL_INCOMPLETE);
            }
            Object details = switch (product.getProductType()) {
                case DEPOSIT -> depositDetails(product, simulation);
                case SAVINGS -> savingsDetails(product, simulation);
                case ETF -> etfDetails(product, simulation);
            };
            return new ProductDetailResponse(
                    simulationId,
                    new SimulationResponse.ProductDataVersion(
                            dataVersion.getProductDataVersionId(),
                            dataVersion.getVersionCode(),
                            dataVersion.getDataDate()
                    ),
                    new ProductDetailResponse.Product(
                            product.getProductVersionId(),
                            product.getProductId(),
                            product.getProductCode(),
                            product.getProductName(),
                            product.getProductType(),
                            product.getDescription(),
                            product.getProductUrl(),
                            product.getSalesStatus(),
                            details
                    )
            );
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "Product detail read failed. simulationId={}, productVersionId={}",
                    simulationId,
                    productVersionId,
                    exception
            );
            throw new SimulationException(
                    SimulationError.PRODUCT_DETAIL_READ_FAILED,
                    exception
            );
        }
    }

    private ProductDetailResponse.DepositDetails depositDetails(
            ProductVersionDetailRecord product,
            SimulationRecord simulation
    ) {
        if (product.getMinimumAmount() == null
                || product.getMinimumMonths() == null
                || product.getMaximumMonths() == null) {
            throw new SimulationException(
                    SimulationError.PRODUCT_TYPE_DETAIL_MISMATCH);
        }
        RateContext rates = rateContext(product.getProductVersionId());
        return new ProductDetailResponse.DepositDetails(
                new ProductDetailResponse.AmountRange(
                        product.getMinimumAmount(),
                        product.getMaximumAmount()
                ),
                new ProductDetailResponse.Term(
                        product.getMinimumMonths(),
                        product.getMaximumMonths()
                ),
                rates.summary(),
                rates.tiers(),
                rates.conditions(),
                new ProductDetailResponse.CalculationPolicy(
                        product.getProductType() == ProductType.DEPOSIT
                                ? com.example.project.simulation.domain.CalculationType.SIMPLE_INTEREST
                                : null,
                        simulation.getFormulaVersion(),
                        null,
                        null,
                        "원금에 연이율과 실제 운용 기간을 적용하는 단리 방식입니다."
                )
        );
    }

    private ProductDetailResponse.SavingsDetails savingsDetails(
            ProductVersionDetailRecord product,
            SimulationRecord simulation
    ) {
        if (product.getSavingsCategory() == null
                || product.getMonthlyMinimumAmount() == null
                || product.getMonthlyMaximumAmount() == null
                || product.getMinimumMonths() == null
                || product.getMaximumMonths() == null) {
            throw new SimulationException(
                    SimulationError.PRODUCT_TYPE_DETAIL_MISMATCH);
        }
        RateContext rates = rateContext(product.getProductVersionId());
        return new ProductDetailResponse.SavingsDetails(
                product.getSavingsCategory(),
                new ProductDetailResponse.AmountRange(
                        product.getMonthlyMinimumAmount(),
                        product.getMonthlyMaximumAmount()
                ),
                new ProductDetailResponse.Term(
                        product.getMinimumMonths(),
                        product.getMaximumMonths()
                ),
                rates.summary(),
                rates.tiers(),
                rates.conditions(),
                new ProductDetailResponse.CalculationPolicy(
                        com.example.project.simulation.domain.CalculationType.MONTHLY_INSTALLMENT,
                        simulation.getFormulaVersion(),
                        "END_OF_MONTH",
                        null,
                        "매월 납입금에 납입일부터 평가일까지의 기간을 적용하는 월 적립식 방식입니다."
                )
        );
    }

    private ProductDetailResponse.EtfDetails etfDetails(
            ProductVersionDetailRecord product,
            SimulationRecord simulation
    ) {
        if (product.getStockCode() == null
                || product.getEtfCategory() == null
                || product.getTrackingIndex() == null
                || product.getAnnualizedReturn5yPercent() == null
                || product.getBondRatioPercent() == null
                || product.getRiskLevel() == null) {
            throw new SimulationException(
                    SimulationError.PRODUCT_TYPE_DETAIL_MISMATCH);
        }
        List<EtfHoldingRecord> holdings = safeList(
                simulationMapper.selectEtfHoldings(
                        product.getProductVersionId(),
                        MAX_HOLDINGS
                )
        );
        BigDecimal topWeight = holdings.stream()
                .map(EtfHoldingRecord::getWeightPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal otherWeight = ONE_HUNDRED.subtract(topWeight)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new ProductDetailResponse.EtfDetails(
                product.getStockCode(),
                product.getEtfCategory(),
                product.getTrackingIndex(),
                product.getAnnualizedReturn5yPercent(),
                product.getBondRatioPercent(),
                product.getRiskLevel(),
                "연 평균 수익률은 최근 5년 데이터를 기준으로 계산한 값이며, 미래 수익을 보장하지 않습니다.",
                holdings.stream()
                        .map(item -> new ProductDetailResponse.Holding(
                                item.getRank(),
                                item.getHoldingName(),
                                item.getHoldingCode(),
                                item.getAssetType(),
                                item.getCountryCode(),
                                item.getWeightPercent()
                        ))
                        .toList(),
                topWeight,
                otherWeight,
                new ProductDetailResponse.CalculationPolicy(
                        com.example.project.simulation.domain.CalculationType.COMPOUND_RETURN,
                        simulation.getFormulaVersion(),
                        null,
                        "ANNUALIZED_RETURN_5Y",
                        "최근 5년 연환산수익률을 실제 운용 기간에 복리로 적용합니다."
                )
        );
    }

    private RateContext rateContext(Long productVersionId) {
        List<BaseRateRecord> baseRates =
                safeList(simulationMapper.selectBaseRates(productVersionId));
        if (baseRates.isEmpty()) {
            throw new SimulationException(
                    SimulationError.PRODUCT_DETAIL_INCOMPLETE);
        }
        List<PreferentialRateRecord> preferentialRates =
                safeList(simulationMapper.selectPreferentialRates(productVersionId));
        BigDecimal minimumBase = baseRates.stream()
                .map(BaseRateRecord::getBaseRatePercent)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal maximumBase = baseRates.stream()
                .map(BaseRateRecord::getBaseRatePercent)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal maximumRate = baseRates.stream()
                .map(BaseRateRecord::getMaximumRatePercent)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        return new RateContext(
                new ProductDetailResponse.RateSummary(
                        minimumBase,
                        maximumBase,
                        maximumRate
                ),
                baseRates.stream()
                        .map(item -> new ProductDetailResponse.BaseRateTier(
                                item.getMinimumMonths(),
                                item.getMaximumMonths(),
                                item.getBaseRatePercent(),
                                item.getMaximumRatePercent()
                        ))
                        .toList(),
                preferentialRates.stream()
                        .map(item -> new ProductDetailResponse.PreferentialCondition(
                                item.getConditionCode(),
                                item.getConditionName(),
                                item.getDescription(),
                                item.getAdditionalRatePercent(),
                                item.getMinimumMonths(),
                                item.getMaximumMonths()
                        ))
                        .toList()
        );
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record RateContext(
            ProductDetailResponse.RateSummary summary,
            List<ProductDetailResponse.BaseRateTier> tiers,
            List<ProductDetailResponse.PreferentialCondition> conditions
    ) {
    }
}
