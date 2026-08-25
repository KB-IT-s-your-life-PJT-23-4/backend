package com.example.project.admin.report.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportMapperXmlTest {

    private static final String NAMESPACE =
            "com.example.project.admin.report.mapper.ReportMapper.";

    @Test
    @DisplayName("신고 목록 조회에 상태, 신고 유형, 페이지 조건을 적용한다")
    void selectAiReportsPageAppliesFiltersAndPagination() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = parameters("OPEN", "JAILBREAK");
        parameters.put("offset", 20L);
        parameters.put("size", 20);

        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + "selectAiReportsPage"
        ).getBoundSql(parameters);
        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("WHERE status = ? AND report_type = ?"));
        assertTrue(sql.contains(
                "ORDER BY r.created_at DESC, r.ai_safety_report_id DESC"
        ));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
    }

    @Test
    @DisplayName("신고 유형에 따라 단건 또는 누적 마스킹 질문을 조회한다")
    void selectReportQuestionExcerptsAppliesReportTypeRange() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("reportIds", java.util.List.of(10L, 11L));

        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + "selectReportQuestionExcerpts"
        ).getBoundSql(parameters);
        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("e.question_excerpt"));
        assertTrue(sql.contains(
                "r.report_type = 'JAILBREAK' AND e.ai_consultation_event_id = r.trigger_event_id"
        ));
        assertTrue(sql.contains(
                "r.report_type = 'OTHER_THRESHOLD' AND e.user_id = r.user_id"
        ));
        assertTrue(sql.contains("LOWER(e.intent) = 'other'"));
        assertTrue(sql.contains("e.occurred_at >= r.count_window_started_at"));
        assertTrue(sql.contains("e.occurred_at <= r.count_window_ended_at"));
        assertTrue(sql.contains("WHERE r.ai_safety_report_id IN ( ? , ? )"));
    }

    @Test
    @DisplayName("신고 전체 건수 조회에도 목록과 동일한 필터를 적용한다")
    void countAiReportsAppliesSameFilters() throws Exception {
        Configuration configuration = configuration();

        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + "countAiReports"
        ).getBoundSql(parameters("IN_REVIEW", "OTHER_THRESHOLD"));
        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("WHERE status = ? AND report_type = ?"));
        assertFalse(sql.contains("LIMIT"));
        assertFalse(sql.contains("OFFSET"));
    }

    @Test
    @DisplayName("필터가 없으면 신고 전체를 조회한다")
    void selectAiReportsPageWithoutFiltersHasNoWhereClause() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = parameters(null, null);
        parameters.put("offset", 0L);
        parameters.put("size", 20);

        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + "selectAiReportsPage"
        ).getBoundSql(parameters);
        String sql = normalize(boundSql.getSql());

        assertFalse(sql.contains(" WHERE "));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
    }

    @Test
    @DisplayName("신고 처리 시 상태, 담당 관리자, 처리 내용과 검토 완료 시각을 수정한다")
    void updateReportUpdatesProcessingFields() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("reportId", 10L);
        parameters.put("status", "RESOLVED");
        parameters.put("adminId", 7L);
        parameters.put("resolutionNote", null);

        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + "updateReport"
        ).getBoundSql(parameters);
        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("SET status = ?"));
        assertTrue(sql.contains("assigned_admin_id = ?"));
        assertTrue(sql.contains("resolution_note = ?"));
        assertTrue(sql.contains("WHEN ? IN ('RESOLVED', 'DISMISSED') THEN CURRENT_TIMESTAMP(6)"));
        assertTrue(sql.contains("ELSE NULL END"));
        assertTrue(sql.contains("WHERE ai_safety_report_id = ?"));
    }

    private Map<String, Object> parameters(String status, String reportType) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", status);
        parameters.put("reportType", reportType);
        return parameters;
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/admin/report/ReportMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder builder = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            );
            builder.parse();
        }
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
