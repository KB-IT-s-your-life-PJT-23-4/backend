package com.example.project.admin.dashboard.service;

import com.example.project.admin.dashboard.domain.DailySignupCount;
import com.example.project.admin.dashboard.domain.LatestProductDataVersion;
import com.example.project.admin.dashboard.domain.ProductTypeCount;
import com.example.project.admin.dashboard.domain.SimulationDashboardCount;
import com.example.project.admin.dashboard.dto.response.AdminDashboardPeriodResponse;
import com.example.project.admin.dashboard.dto.response.AdminDashboardResponse;
import com.example.project.admin.dashboard.dto.response.AdminProductSummaryResponse;
import com.example.project.admin.dashboard.dto.response.AdminSignupSummaryResponse;
import com.example.project.admin.dashboard.dto.response.AdminSimulationSummaryResponse;
import com.example.project.admin.dashboard.mapper.AdminDashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DASHBOARD_DAYS = 7;

    private final AdminDashboardMapper adminDashboardMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        Clock serviceClock = clock.withZone(SERVICE_ZONE);
        LocalDate today = LocalDate.now(serviceClock);
        LocalDate periodStart = today.minusDays(DASHBOARD_DAYS - 1L);
        LocalDateTime startDateTime = periodStart.atStartOfDay();
        LocalDateTime endDateTime = today.plusDays(1).atStartOfDay();

        AdminSignupSummaryResponse signups = signupSummary(
                periodStart,
                today,
                startDateTime,
                endDateTime
        );
        AdminSimulationSummaryResponse simulations = simulationSummary(
                startDateTime,
                endDateTime
        );
        AdminProductSummaryResponse products = productSummary();

        return new AdminDashboardResponse(
                "database",
                OffsetDateTime.now(serviceClock),
                new AdminDashboardPeriodResponse(periodStart, today),
                signups,
                AdminDashboardResponse.unavailableConsultations(),
                AdminDashboardResponse.unavailableFastApi(),
                AdminDashboardResponse.unavailableErrors(),
                simulations,
                products
        );
    }

    private AdminSignupSummaryResponse signupSummary(
            LocalDate periodStart,
            LocalDate today,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        Map<LocalDate, Long> countsByDate = new HashMap<>();
        List<DailySignupCount> rows = adminDashboardMapper.selectDailySignupCounts(
                startDateTime,
                endDateTime
        );

        if (rows != null) {
            for (DailySignupCount row : rows) {
                if (row == null || row.getDate() == null
                        || row.getDate().isBefore(periodStart)
                        || row.getDate().isAfter(today)) {
                    continue;
                }
                countsByDate.merge(row.getDate(), nonNegative(row.getCount()), Long::sum);
            }
        }

        List<AdminSignupSummaryResponse.DailySignupResponse> trend =
                java.util.stream.IntStream.range(0, DASHBOARD_DAYS)
                        .mapToObj(index -> periodStart.plusDays(index))
                        .map(date -> new AdminSignupSummaryResponse.DailySignupResponse(
                                date,
                                countsByDate.getOrDefault(date, 0L)
                        ))
                        .toList();
        long last7Days = trend.stream()
                .mapToLong(AdminSignupSummaryResponse.DailySignupResponse::getCount)
                .sum();

        return new AdminSignupSummaryResponse(
                countsByDate.getOrDefault(today, 0L),
                last7Days,
                trend
        );
    }

    private AdminSimulationSummaryResponse simulationSummary(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        SimulationDashboardCount counts = adminDashboardMapper.selectSimulationCounts(
                startDateTime,
                endDateTime
        );
        long runs = counts == null ? 0L : nonNegative(counts.getRuns());
        long saves = counts == null ? 0L : nonNegative(counts.getSaves());

        BigDecimal saveRate = runs == 0L
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(saves)
                        .multiply(BigDecimal.valueOf(100L))
                        .divide(BigDecimal.valueOf(runs), 1, RoundingMode.HALF_UP);

        return new AdminSimulationSummaryResponse(runs, saves, saveRate);
    }

    private AdminProductSummaryResponse productSummary() {
        LatestProductDataVersion version =
                adminDashboardMapper.selectLatestCompletedProductDataVersion();
        if (version == null || version.getProductDataVersionId() == null) {
            return AdminProductSummaryResponse.unavailable();
        }

        ProductTypeCount counts = adminDashboardMapper.selectProductTypeCounts(
                version.getProductDataVersionId()
        );

        return new AdminProductSummaryResponse(
                true,
                version.getDataDate(),
                version.getVersionCode(),
                counts == null ? 0L : nonNegative(counts.getDeposits()),
                counts == null ? 0L : nonNegative(counts.getSavings()),
                counts == null ? 0L : nonNegative(counts.getEtfs())
        );
    }

    private long nonNegative(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }
}
