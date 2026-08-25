package com.example.project.admin.dashboard.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastApiHealthClientTest {

    @Test
    @DisplayName("FastAPI 헬스 응답이 UP이면 정상 상태와 응답 시간을 반환한다")
    void returnHealthyMetricsForUpResponse() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(jsonResponse(
                        HttpStatus.OK,
                        "{\"status\":\"UP\"}"
                )))
                .build();

        var metrics = new FastApiHealthClient(webClient).check();

        assertTrue(metrics.isAvailable());
        assertEquals("healthy", metrics.getStatus());
        assertNotNull(metrics.getAverageResponseMs());
        assertTrue(metrics.getAverageResponseMs() >= 0L);
    }

    @Test
    @DisplayName("FastAPI 연결이 실패하면 대시보드 요청을 실패시키지 않고 위험 상태를 반환한다")
    void returnDangerMetricsWhenHealthRequestFails() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IllegalStateException("offline")))
                .build();

        var metrics = new FastApiHealthClient(webClient).check();

        assertTrue(metrics.isAvailable());
        assertEquals("danger", metrics.getStatus());
        assertNull(metrics.getAverageResponseMs());
    }

    private ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
