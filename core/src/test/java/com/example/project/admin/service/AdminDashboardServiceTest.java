package com.example.project.admin.service;

import com.example.project.admin.domain.DailySignupCount;
import com.example.project.admin.domain.LatestProductDataVersion;
import com.example.project.admin.domain.ProductTypeCount;
import com.example.project.admin.domain.SimulationDashboardCount;
import com.example.project.admin.dto.response.AdminSignupSummaryResponse;
import com.example.project.admin.mapper.AdminDashboardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDashboardServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private FakeAdminDashboardMapper mapper;
    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeAdminDashboardMapper();
        Clock fixedClock = Clock.fixed(
                Instant.parse("2027-01-02T06:00:00Z"),
                SEOUL
        );
        service = new AdminDashboardService(mapper, fixedClock);
    }

    @Test
    @DisplayName("한국 시간의 연도 경계에서도 7일 가입자·시뮬레이션·상품 통계를 조립한다")
    void assembleDashboardAcrossYearBoundary() {
        mapper.dailySignupCounts.add(dailyCount(LocalDate.of(2027, 1, 2), 4L));
        mapper.dailySignupCounts.add(dailyCount(LocalDate.of(2026, 12, 28), 2L));
        mapper.dailySignupCounts.add(dailyCount(LocalDate.of(2026, 12, 31), 3L));
        mapper.simulationCounts = simulationCounts(3L, 2L);
        mapper.latestProductDataVersion = productVersion(
                10L,
                LocalDate.of(2027, 1, 1),
                "2027.01-r1"
        );
        mapper.productTypeCounts = productCounts(10L, null, 4L);

        var response = service.getDashboard();

        assertEquals("database", response.getSource());
        assertEquals(LocalDate.of(2026, 12, 27), response.getPeriod().getFrom());
        assertEquals(LocalDate.of(2027, 1, 2), response.getPeriod().getTo());
        assertEquals(ZoneOffset.ofHours(9), response.getUpdatedAt().getOffset());

        assertEquals(
                LocalDateTime.of(2026, 12, 27, 0, 0),
                mapper.signupStartDateTime
        );
        assertEquals(
                LocalDateTime.of(2027, 1, 3, 0, 0),
                mapper.signupEndDateTime
        );
        assertEquals(mapper.signupStartDateTime, mapper.simulationStartDateTime);
        assertEquals(mapper.signupEndDateTime, mapper.simulationEndDateTime);

        assertEquals(4L, response.getSignups().getToday());
        assertEquals(9L, response.getSignups().getLast7Days());
        assertEquals(7, response.getSignups().getTrend().size());
        assertEquals(0L, response.getSignups().getTrend().get(0).getCount());
        assertEquals(
                List.of(
                        LocalDate.of(2026, 12, 27),
                        LocalDate.of(2026, 12, 28),
                        LocalDate.of(2026, 12, 29),
                        LocalDate.of(2026, 12, 30),
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2027, 1, 1),
                        LocalDate.of(2027, 1, 2)
                ),
                response.getSignups().getTrend().stream()
                        .map(AdminSignupSummaryResponse.DailySignupResponse::getDate)
                        .toList()
        );

        assertEquals(3L, response.getSimulations().getRuns());
        assertEquals(2L, response.getSimulations().getSaves());
        assertEquals(new BigDecimal("66.7"), response.getSimulations().getSaveRate());

        assertTrue(response.getProducts().isAvailable());
        assertEquals(LocalDate.of(2027, 1, 1), response.getProducts().getAsOfDate());
        assertEquals("2027.01-r1", response.getProducts().getVersion());
        assertEquals(10L, response.getProducts().getDeposits());
        assertEquals(0L, response.getProducts().getSavings());
        assertEquals(4L, response.getProducts().getEtfs());
        assertEquals(10L, mapper.productCountVersionId);

        assertFalse(response.getConsultations().isAvailable());
        assertNull(response.getConsultations().getRequests());
        assertNull(response.getConsultations().getSuccessRate());
        assertFalse(response.getFastApi().isAvailable());
        assertEquals("unavailable", response.getFastApi().getStatus());
        assertNull(response.getFastApi().getAverageResponseMs());
        assertFalse(response.getErrors().isAvailable());
        assertNull(response.getErrors().getHttp500());
    }

    @Test
    @DisplayName("실행과 완료 상품 버전이 없으면 0.0 전환율과 명시적인 미등록 상태를 반환한다")
    void returnEmptyStatesWithoutFakeMetrics() {
        var response = service.getDashboard();

        assertEquals(0L, response.getSignups().getToday());
        assertEquals(0L, response.getSignups().getLast7Days());
        assertEquals(7, response.getSignups().getTrend().size());
        assertTrue(response.getSignups().getTrend().stream()
                .allMatch(item -> item.getCount() == 0L));
        assertEquals(0L, response.getSimulations().getRuns());
        assertEquals(0L, response.getSimulations().getSaves());
        assertEquals(new BigDecimal("0.0"), response.getSimulations().getSaveRate());
        assertFalse(response.getProducts().isAvailable());
        assertNull(response.getProducts().getAsOfDate());
        assertNull(response.getProducts().getVersion());
        assertEquals(0L, response.getProducts().getDeposits());
        assertEquals(0L, response.getProducts().getSavings());
        assertEquals(0L, response.getProducts().getEtfs());
        assertNull(mapper.productCountVersionId);
    }

    @Test
    @DisplayName("저장 전환율은 HALF_UP 기준으로 소수점 첫째 자리까지 반올림한다")
    void roundSaveRateToOneDecimalPlace() {
        mapper.simulationCounts = simulationCounts(6L, 1L);

        var response = service.getDashboard();

        assertEquals(new BigDecimal("16.7"), response.getSimulations().getSaveRate());
    }

    private DailySignupCount dailyCount(LocalDate date, Long count) {
        DailySignupCount row = new DailySignupCount();
        row.setDate(date);
        row.setCount(count);
        return row;
    }

    private SimulationDashboardCount simulationCounts(Long runs, Long saves) {
        SimulationDashboardCount counts = new SimulationDashboardCount();
        counts.setRuns(runs);
        counts.setSaves(saves);
        return counts;
    }

    private LatestProductDataVersion productVersion(Long id, LocalDate date, String version) {
        LatestProductDataVersion dataVersion = new LatestProductDataVersion();
        dataVersion.setProductDataVersionId(id);
        dataVersion.setDataDate(date);
        dataVersion.setVersionCode(version);
        return dataVersion;
    }

    private ProductTypeCount productCounts(Long deposits, Long savings, Long etfs) {
        ProductTypeCount counts = new ProductTypeCount();
        counts.setDeposits(deposits);
        counts.setSavings(savings);
        counts.setEtfs(etfs);
        return counts;
    }

    private static final class FakeAdminDashboardMapper implements AdminDashboardMapper {

        private final List<DailySignupCount> dailySignupCounts = new ArrayList<>();
        private SimulationDashboardCount simulationCounts;
        private LatestProductDataVersion latestProductDataVersion;
        private ProductTypeCount productTypeCounts;
        private LocalDateTime signupStartDateTime;
        private LocalDateTime signupEndDateTime;
        private LocalDateTime simulationStartDateTime;
        private LocalDateTime simulationEndDateTime;
        private Long productCountVersionId;

        @Override
        public List<DailySignupCount> selectDailySignupCounts(
                LocalDateTime startDateTime,
                LocalDateTime endDateTime
        ) {
            signupStartDateTime = startDateTime;
            signupEndDateTime = endDateTime;
            return dailySignupCounts;
        }

        @Override
        public SimulationDashboardCount selectSimulationCounts(
                LocalDateTime startDateTime,
                LocalDateTime endDateTime
        ) {
            simulationStartDateTime = startDateTime;
            simulationEndDateTime = endDateTime;
            return simulationCounts;
        }

        @Override
        public LatestProductDataVersion selectLatestCompletedProductDataVersion() {
            return latestProductDataVersion;
        }

        @Override
        public ProductTypeCount selectProductTypeCounts(Long productDataVersionId) {
            productCountVersionId = productDataVersionId;
            return productTypeCounts;
        }
    }
}
