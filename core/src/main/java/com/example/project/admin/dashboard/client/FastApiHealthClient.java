package com.example.project.admin.dashboard.client;

import com.example.project.admin.dashboard.dto.response.AdminDashboardResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Log4j2
public class FastApiHealthClient implements FastApiHealthMonitor {

    static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);
    private static final long WARNING_RESPONSE_MS = 1_000L;

    private final WebClient webClient;

    public FastApiHealthClient(@Qualifier("fastApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminDashboardResponse.FastApiMetrics check() {
        long startedAt = System.nanoTime();

        try {
            FastApiHealthResponse response = webClient.get()
                    .uri("/api/v1/health")
                    .retrieve()
                    .bodyToMono(FastApiHealthResponse.class)
                    .timeout(HEALTH_CHECK_TIMEOUT)
                    .block();
            long elapsedMs = elapsedMillis(startedAt);

            if (response == null || !"UP".equalsIgnoreCase(response.getStatus())) {
                return new AdminDashboardResponse.FastApiMetrics(true, elapsedMs, "danger");
            }

            String status = elapsedMs >= WARNING_RESPONSE_MS ? "warning" : "healthy";
            return new AdminDashboardResponse.FastApiMetrics(true, elapsedMs, status);
        } catch (RuntimeException exception) {
            log.warn("FastAPI health check failed: {}", exception.getClass().getSimpleName());
            return new AdminDashboardResponse.FastApiMetrics(true, null, "danger");
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class FastApiHealthResponse {

        private String status;
    }
}
