package com.example.project.admin.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class AdminDashboardResponse {

    private final String source;
    private final OffsetDateTime updatedAt;
    private final AdminDashboardPeriodResponse period;
    private final AdminSignupSummaryResponse signups;
    private final ConsultationMetrics consultations;
    private final FastApiMetrics fastApi;
    private final ErrorMetrics errors;
    private final AdminSimulationSummaryResponse simulations;
    private final AdminProductSummaryResponse products;

    public static ConsultationMetrics unavailableConsultations() {
        return new ConsultationMetrics(false, null, null, null, null);
    }

    public static FastApiMetrics unavailableFastApi() {
        return new FastApiMetrics(false, null, "unavailable");
    }

    public static ErrorMetrics unavailableErrors() {
        return new ErrorMetrics(false, null, null, null);
    }

    @Getter
    @AllArgsConstructor
    public static class ConsultationMetrics {

        private final boolean available;
        private final Long requests;
        private final Long successes;
        private final Long failures;
        private final BigDecimal successRate;
    }

    @Getter
    @AllArgsConstructor
    public static class FastApiMetrics {

        private final boolean available;
        private final Long averageResponseMs;
        private final String status;
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorMetrics {

        private final boolean available;
        private final Long http422;
        private final Long http500;
        private final Long timeout;
    }
}
