package com.example.project.common.logging;

import com.example.project.admin.dashboard.domain.AdminDashboardErrorCount;
import com.example.project.admin.dashboard.domain.DailySignupCount;
import com.example.project.admin.dashboard.domain.LatestProductDataVersion;
import com.example.project.admin.dashboard.domain.ProductTypeCount;
import com.example.project.admin.dashboard.domain.SimulationDashboardCount;
import com.example.project.admin.dashboard.mapper.AdminDashboardMapper;
import com.example.project.admin.dashboard.service.AdminDashboardErrorRecorder;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminDashboardErrorInterceptorTest {

    private CapturingDashboardMapper mapper;
    private AdminDashboardErrorInterceptor interceptor;

    @BeforeEach
    void setUp() {
        mapper = new CapturingDashboardMapper();
        AdminDashboardErrorRecorder recorder = new AdminDashboardErrorRecorder(
                mapper,
                Clock.fixed(
                        Instant.parse("2026-08-18T03:00:00Z"),
                        ZoneId.of("Asia/Seoul")
                )
        );
        interceptor = new AdminDashboardErrorInterceptor(recorder);
    }

    @Test
    @DisplayName("사용자 API의 실제 HTTP 422와 500만 기록하고 다른 5xx는 제외한다")
    void recordsOnlyExactHttpStatuses() throws Exception {
        MockMvc mockMvc = standaloneSetup(new StatusController())
                .addInterceptors(interceptor)
                .build();

        mockMvc.perform(get("/api/test/status/422"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/test/status/500"))
                .andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/test/status/503"))
                .andExpect(status().isServiceUnavailable());

        assertEquals(2, mapper.logs.size());
        assertEquals(422, mapper.logs.get(0).responseStatus);
        assertEquals(AdminDashboardErrorRecorder.RESULT_HTTP_ERROR, mapper.logs.get(0).result);
        assertEquals(500, mapper.logs.get(1).responseStatus);
        assertEquals(AdminDashboardErrorRecorder.RESULT_HTTP_ERROR, mapper.logs.get(1).result);
    }

    @Test
    @DisplayName("타임아웃과 HTTP 500은 한 요청의 한 행에 기록되어 독립 집계할 수 있다")
    void recordsTimeoutAndHttp500Once() {
        MockHttpServletRequest request = request("/api/ai/consult");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        interceptor.preHandle(request, response, new Object());
        ApiErrorTrackingContext.markTimeout(request);

        interceptor.afterCompletion(request, response, new Object(), null);
        interceptor.afterCompletion(request, response, new Object(), null);

        assertEquals(1, mapper.logs.size());
        assertEquals(500, mapper.logs.get(0).responseStatus);
        assertEquals(AdminDashboardErrorRecorder.RESULT_TIMEOUT, mapper.logs.get(0).result);
    }

    @Test
    @DisplayName("DeferredResult의 중첩된 타임아웃 예외도 TIMEOUT으로 표시한다")
    void marksDeferredTimeoutException() {
        MockHttpServletRequest request = request("/api/ai/consult");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        interceptor.preHandle(request, response, new Object());

        DeferredResult<String> deferredResult = new DeferredResult<>();
        ServiceException timeout = new ServiceException(
                ResponseCode.EXTERNAL_API_TIMEOUT,
                new RuntimeException(new TimeoutException("FastAPI exceeded 25 seconds"))
        );
        new DeferredAccessLogInterceptor().handleError(
                new ServletWebRequest(request),
                deferredResult,
                timeout
        );

        interceptor.afterCompletion(request, response, new Object(), null);

        assertEquals(1, mapper.logs.size());
        assertEquals(AdminDashboardErrorRecorder.RESULT_TIMEOUT, mapper.logs.get(0).result);
    }

    @Test
    @DisplayName("관리자·헬스체크·정적 경로는 오류여도 기록하지 않는다")
    void excludesNonUserApis() {
        complete("/api/admin/dashboard", 500, true);
        complete("/api/health", 500, true);
        complete("/swagger-ui.html", 500, true);

        assertEquals(0, mapper.logs.size());
    }

    @Test
    @DisplayName("느리더라도 정상 완료된 요청은 타임아웃으로 기록하지 않는다")
    void ignoresSlowSuccessfulRequestWithoutTimeoutSignal() {
        complete("/api/ai/consult", 200, false);

        assertEquals(0, mapper.logs.size());
    }

    @Test
    @DisplayName("오류 지표 저장 실패는 원래 사용자 API 완료 흐름을 실패시키지 않는다")
    void ignoresPersistenceFailure() {
        mapper.failOnInsert = true;

        assertDoesNotThrow(() -> complete("/api/users/me", 500, false));
    }

    private void complete(String uri, int status, boolean timeout) {
        MockHttpServletRequest request = request(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        interceptor.preHandle(request, response, new Object());
        if (timeout) {
            ApiErrorTrackingContext.markTimeout(request);
        }
        interceptor.afterCompletion(request, response, new Object(), null);
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static final class CapturingDashboardMapper implements AdminDashboardMapper {

        private final List<CapturedLog> logs = new ArrayList<>();
        private boolean failOnInsert;

        @Override
        public List<DailySignupCount> selectDailySignupCounts(
                LocalDateTime startDateTime,
                LocalDateTime endDateTime
        ) {
            return List.of();
        }

        @Override
        public SimulationDashboardCount selectSimulationCounts(
                LocalDateTime startDateTime,
                LocalDateTime endDateTime
        ) {
            return null;
        }

        @Override
        public LatestProductDataVersion selectLatestCompletedProductDataVersion() {
            return null;
        }

        @Override
        public ProductTypeCount selectProductTypeCounts(Long productDataVersionId) {
            return null;
        }

        @Override
        public AdminDashboardErrorCount selectErrorCounts(
                LocalDateTime startDateTime,
                LocalDateTime endDateTime
        ) {
            return null;
        }

        @Override
        public int insertApiErrorLog(
                String httpMethod,
                String requestUri,
                int responseStatus,
                String result,
                long elapsedMs,
                LocalDateTime occurredAt
        ) {
            if (failOnInsert) {
                throw new IllegalStateException("metric storage unavailable");
            }
            logs.add(new CapturedLog(responseStatus, result));
            return 1;
        }
    }

    @RestController
    private static final class StatusController {

        @GetMapping("/api/test/status/{status}")
        public ResponseEntity<Void> status(@PathVariable int status) {
            return ResponseEntity.status(status).build();
        }
    }

    private static final class CapturedLog {

        private final int responseStatus;
        private final String result;

        private CapturedLog(int responseStatus, String result) {
            this.responseStatus = responseStatus;
            this.result = result;
        }
    }
}
