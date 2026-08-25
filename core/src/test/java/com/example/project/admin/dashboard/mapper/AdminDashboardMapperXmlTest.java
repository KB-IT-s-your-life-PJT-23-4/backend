package com.example.project.admin.dashboard.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDashboardMapperXmlTest {

    private static final String NAMESPACE =
            "com.example.project.admin.dashboard.mapper.AdminDashboardMapper.";

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/admin/dashboard/AdminDashboardMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }
    }

    @Test
    @DisplayName("Mapper 인터페이스 메서드와 XML statement ID가 정확히 일치한다")
    void matchMapperMethodsAndStatements() {
        Set<String> expectedIds = Arrays.stream(AdminDashboardMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "selectDailySignupCounts",
                        "selectSimulationCounts",
                        "selectConsultationCounts",
                        "selectLatestCompletedProductDataVersion",
                        "selectProductTypeCounts",
                        "selectErrorCounts",
                        "insertApiErrorLog"
                ),
                expectedIds
        );
        expectedIds.forEach(id -> assertTrue(configuration.hasStatement(NAMESPACE + id)));
    }

    @Test
    @DisplayName("AI 상담 요청과 성공·실패는 최근 기간의 응답 상태를 기준으로 집계한다")
    void aggregateConsultationsByResponseStatus() {
        Map<String, Object> parameters = Map.of(
                "startDateTime", LocalDateTime.of(2026, 8, 12, 0, 0),
                "endDateTime", LocalDateTime.of(2026, 8, 19, 0, 0)
        );

        String consultationSql = sql("selectConsultationCounts", parameters);

        assertTrue(consultationSql.contains("FROM ai_consultation_event"));
        assertTrue(consultationSql.contains(
                "response_status IN ('COMPLETED', 'CLARIFICATION_REQUIRED')"
        ));
        assertTrue(consultationSql.contains("response_status = 'REJECTED'"));
        assertTrue(consultationSql.contains("occurred_at >= ? AND occurred_at < ?"));
    }

    @Test
    @DisplayName("가입자와 시뮬레이션 기간 조건은 컬럼 함수 없이 반개방 범위를 사용한다")
    void useIndexFriendlyHalfOpenDateRanges() {
        Map<String, Object> parameters = Map.of(
                "startDateTime", LocalDateTime.of(2026, 7, 31, 0, 0),
                "endDateTime", LocalDateTime.of(2026, 8, 7, 0, 0)
        );
        String signupSql = sql("selectDailySignupCounts", parameters);
        String simulationSql = sql("selectSimulationCounts", parameters);

        assertTrue(signupSql.contains("WHERE created_at >= ? AND created_at < ?"));
        assertTrue(signupSql.contains("role = 'USER'"));
        assertTrue(signupSql.contains("GROUP BY DATE(created_at)"));
        String signupWhere = signupSql.substring(
                signupSql.indexOf("WHERE"),
                signupSql.indexOf("GROUP BY")
        );
        assertFalse(signupWhere.contains("DATE(created_at)"));

        assertTrue(simulationSql.contains("WHERE created_at >= ? AND created_at < ?"));
        assertTrue(simulationSql.contains("status = 'SAVED'"));
        assertFalse(simulationSql.contains("DATE(created_at)"));
    }

    @Test
    @DisplayName("최신 완료 상품 버전과 해당 버전 전체 상품 유형을 기준대로 조회한다")
    void selectLatestCompletedVersionAndAllProductTypes() {
        String versionSql = sql("selectLatestCompletedProductDataVersion", null);
        String countSql = sql("selectProductTypeCounts", Map.of(
                "productDataVersionId", 10L
        ));

        assertTrue(versionSql.contains("WHERE status = 'COMPLETED'"));
        assertTrue(versionSql.contains(
                "ORDER BY data_date DESC, completed_at DESC, kb_product_data_version_id DESC"
        ));
        assertTrue(versionSql.endsWith("LIMIT 1"));

        assertTrue(countSql.contains("FROM kb_product_version pv JOIN kb_product p"));
        assertTrue(countSql.contains("p.product_type = 'DEPOSIT'"));
        assertTrue(countSql.contains("p.product_type = 'SAVINGS'"));
        assertTrue(countSql.contains("p.product_type = 'ETF'"));
        assertTrue(countSql.contains("pv.kb_product_data_version_id = ?"));
        assertFalse(countSql.contains("sales_status"));
    }

    @Test
    @DisplayName("오류 집계는 최근 기간의 실제 422·500 상태와 TIMEOUT 결과를 독립 집계한다")
    void aggregateActualHttpStatusesAndTimeouts() {
        Map<String, Object> parameters = Map.of(
                "startDateTime", LocalDateTime.of(2026, 8, 12, 0, 0),
                "endDateTime", LocalDateTime.of(2026, 8, 19, 0, 0)
        );

        String errorSql = sql("selectErrorCounts", parameters);

        assertTrue(errorSql.contains("response_status = 422"));
        assertTrue(errorSql.contains("response_status = 500"));
        assertTrue(errorSql.contains("result = 'TIMEOUT'"));
        assertTrue(errorSql.contains("occurred_at >= ? AND occurred_at < ?"));
        assertTrue(errorSql.contains("request_uri LIKE '/api/%'"));
        assertTrue(errorSql.contains("request_uri NOT LIKE '/api/admin/%'"));
        assertFalse(errorSql.contains("response_status >= 500"));
    }

    @Test
    @DisplayName("오류 기록은 기존 admin_access_log 컬럼만 사용한다")
    void insertApiErrorWithoutSchemaChange() {
        Map<String, Object> parameters = Map.of(
                "httpMethod", "POST",
                "requestUri", "/api/ai/consult",
                "responseStatus", 500,
                "result", "TIMEOUT",
                "elapsedMs", 25_000L,
                "occurredAt", LocalDateTime.of(2026, 8, 18, 12, 0)
        );

        String insertSql = sql("insertApiErrorLog", parameters);

        assertTrue(insertSql.startsWith("INSERT INTO admin_access_log"));
        assertTrue(insertSql.contains("response_status"));
        assertTrue(insertSql.contains("result"));
        assertTrue(insertSql.contains("occurred_at"));
    }

    private String sql(String statementId, Object parameter) {
        return configuration.getMappedStatement(NAMESPACE + statementId)
                .getBoundSql(parameter)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
