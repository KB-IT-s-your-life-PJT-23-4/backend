package com.example.project.simulation.service;

import com.example.project.simulation.domain.BaseRateRecord;
import com.example.project.simulation.domain.ProductDataVersionRecord;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.ProductVersionDetailRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.TaxPaymentMethod;
import com.example.project.simulation.dto.response.ProductDetailResponse;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationProductServiceTest {

    private static final long USER_ID = 7L;
    private static final long SIMULATION_ID = 9_001L;
    private static final long PRODUCT_VERSION_ID = 401L;

    @Test
    @DisplayName("실행 스냅샷에 포함되지 않은 상품은 상세 조회할 수 없다")
    void rejectProductNotIncludedInSimulation() {
        Fixture fixture = new Fixture();
        fixture.productIncluded = false;

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().getDetail(
                        SIMULATION_ID, PRODUCT_VERSION_ID, USER_ID)
        );

        // 회귀 방지: 같은 데이터 버전이라는 이유만으로 추천하지 않은 상품까지 조회되지 않게 한다.
        assertEquals(SimulationError.PRODUCT_NOT_IN_SIMULATION,
                exception.getError());
    }

    @Test
    @DisplayName("실행 당시와 다른 상품 데이터 버전은 상세 조회할 수 없다")
    void rejectProductFromDifferentDataVersion() {
        Fixture fixture = new Fixture();
        fixture.product.setProductDataVersionId(99L);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().getDetail(
                        SIMULATION_ID, PRODUCT_VERSION_ID, USER_ID)
        );

        // 회귀 방지: 현재 상품 정보가 실행 당시 스냅샷의 상품 정보처럼 섞이지 않게 한다.
        assertEquals(SimulationError.PRODUCT_DATA_VERSION_MISMATCH,
                exception.getError());
    }

    @Test
    @DisplayName("만료된 DRAFT의 상품 상세정보도 조회할 수 없다")
    void rejectProductDetailForExpiredDraft() {
        Fixture fixture = new Fixture();
        fixture.simulation.setExpiredAt(LocalDateTime.now().minusSeconds(1));

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().getDetail(
                        SIMULATION_ID, PRODUCT_VERSION_ID, USER_ID)
        );

        // 회귀 방지: 단건 조회와 상품 상세 조회의 DRAFT 만료 정책이 달라지지 않도록 한다.
        assertEquals(SimulationError.SIMULATION_EXPIRED, exception.getError());
    }

    @Test
    @DisplayName("예금 상세 필수값이 누락되면 상품 유형 불일치로 처리한다")
    void rejectIncompleteDepositDetails() {
        Fixture fixture = new Fixture();
        fixture.product.setMinimumAmount(null);

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().getDetail(
                        SIMULATION_ID, PRODUCT_VERSION_ID, USER_ID)
        );

        // 회귀 방지: 예금 한도·기간이 없는 불완전한 상품을 정상 상세정보로 노출하지 않는다.
        assertEquals(SimulationError.PRODUCT_TYPE_DETAIL_MISMATCH,
                exception.getError());
    }

    @Test
    @DisplayName("예적금 기본금리 구간이 없으면 상세 스냅샷 불완전으로 처리한다")
    void rejectDepositWithoutBaseRateTiers() {
        Fixture fixture = new Fixture();
        fixture.baseRates = List.of();

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> fixture.service().getDetail(
                        SIMULATION_ID, PRODUCT_VERSION_ID, USER_ID)
        );

        // 회귀 방지: 수익률 범위를 계산할 근거가 없는 예적금 상세 응답을 만들지 않는다.
        assertEquals(SimulationError.PRODUCT_DETAIL_INCOMPLETE,
                exception.getError());
    }

    @Test
    @DisplayName("ETF 가격 이력이 없어도 상품 기본 상세정보는 조회하고 변동성은 미제공으로 표시한다")
    void returnEtfDetailWhenPriceHistoryIsUnavailable() {
        Fixture fixture = new Fixture();
        fixture.product = etfProduct();

        ProductDetailResponse response = fixture.service().getDetail(
                SIMULATION_ID, PRODUCT_VERSION_ID, USER_ID
        );
        ProductDetailResponse.EtfDetails details = assertInstanceOf(
                ProductDetailResponse.EtfDetails.class,
                response.product().details()
        );

        // 회귀 방지: 선택적 가격 이력이 없다는 이유로 ETF 상세 조회 전체가 500으로 실패하지 않는다.
        assertFalse(details.volatility().isAvailable());
        assertEquals(0, details.volatility().getPriceObservationCount());
        assertEquals(new BigDecimal("100.00"), details.otherWeightPercent());
    }

    private static final class Fixture {
        private final SimulationRecord simulation = simulation();
        private ProductVersionDetailRecord product = depositProduct();
        private final ProductDataVersionRecord dataVersion = dataVersion();
        private List<BaseRateRecord> baseRates = List.of(baseRate());
        private boolean productIncluded = true;

        private SimulationProductService service() {
            SimulationMapper mapper = mapper();
            SimulationService simulationService = new SimulationService(
                    mapper,
                    userMapper(),
                    new SimulationCalculator(),
                    new SimulationIdempotencyStore(),
                    new EtfVolatilityCalculator()
            );
            return new SimulationProductService(
                    mapper,
                    simulationService,
                    new EtfVolatilityCalculator()
            );
        }

        private SimulationMapper mapper() {
            return (SimulationMapper) Proxy.newProxyInstance(
                    SimulationMapper.class.getClassLoader(),
                    new Class<?>[]{SimulationMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "selectSimulation" -> simulation;
                        case "selectProductVersionDetail" -> product;
                        case "existsProductInSimulation" -> productIncluded;
                        case "selectProductDataVersion" -> dataVersion;
                        case "selectBaseRates" -> baseRates;
                        case "selectPreferentialRates", "selectEtfHoldings",
                                "selectRecentEtfPrices" -> List.of();
                        case "toString" -> "SimulationProductMapperFixture";
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
                        case "toString" -> "SimulationProductUserMapperFixture";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static SimulationRecord simulation() {
        SimulationRecord simulation = new SimulationRecord();
        simulation.setSimulationId(SIMULATION_ID);
        simulation.setProductDataVersionId(51L);
        simulation.setFamilyId(31L);
        simulation.setUserId(USER_ID);
        simulation.setStatus(SimulationStatus.DRAFT);
        simulation.setTaxPaymentMethod(TaxPaymentMethod.RECIPIENT_PAYS);
        simulation.setAsOfDate(LocalDate.now());
        simulation.setGiftDate(LocalDate.now().plusDays(1));
        simulation.setInvestmentEndDate(LocalDate.now().plusYears(3));
        simulation.setFormulaVersion(SimulationService.FORMULA_VERSION);
        simulation.setExpiredAt(LocalDateTime.now().plusDays(1));
        return simulation;
    }

    private static ProductVersionDetailRecord depositProduct() {
        ProductVersionDetailRecord product = commonProduct(ProductType.DEPOSIT);
        product.setMinimumAmount(10_000L);
        product.setMaximumAmount(100_000_000L);
        product.setMinimumMonths(1);
        product.setMaximumMonths(60);
        return product;
    }

    private static ProductVersionDetailRecord etfProduct() {
        ProductVersionDetailRecord product = commonProduct(ProductType.ETF);
        product.setStockCode("123456");
        product.setEtfCategory("BOND_MIXED");
        product.setTrackingIndex("테스트 채권혼합 지수");
        product.setAnnualizedReturn10yPercent(new BigDecimal("5.00"));
        product.setBondRatioPercent(new BigDecimal("40.00"));
        product.setRiskLevel("MEDIUM");
        return product;
    }

    private static ProductVersionDetailRecord commonProduct(ProductType type) {
        ProductVersionDetailRecord product = new ProductVersionDetailRecord();
        product.setProductVersionId(PRODUCT_VERSION_ID);
        product.setProductDataVersionId(51L);
        product.setProductId(501L);
        product.setProductCode("TEST-501");
        product.setProductName("테스트 상품");
        product.setProductType(type);
        product.setSalesStatus("ON_SALE");
        return product;
    }

    private static ProductDataVersionRecord dataVersion() {
        ProductDataVersionRecord version = new ProductDataVersionRecord();
        version.setProductDataVersionId(51L);
        version.setVersionCode("20260812-01");
        version.setDataDate(LocalDate.now());
        version.setStatus("COMPLETED");
        return version;
    }

    private static BaseRateRecord baseRate() {
        BaseRateRecord rate = new BaseRateRecord();
        rate.setBaseInterestRateId(601L);
        rate.setProductVersionId(PRODUCT_VERSION_ID);
        rate.setMinimumMonths(1);
        rate.setMaximumMonths(60);
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
