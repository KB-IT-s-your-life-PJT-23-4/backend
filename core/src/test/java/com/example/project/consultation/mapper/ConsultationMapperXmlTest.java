package com.example.project.consultation.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultationMapperXmlTest {

    private static final String NAMESPACE =
            "com.example.project.consultation.mapper.ConsultationMapper.";

    @Test
    void 사용자잠금은MySql식별자를사용한다() throws Exception {
        BoundSql boundSql = configuration()
                .getMappedStatement(NAMESPACE + "lockUser")
                .getBoundSql(Map.of("userId", 1L));

        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("FROM `user`"));
        assertTrue(sql.endsWith("FOR UPDATE"));
    }

    @Test
    void 활성대화조회는인덱스갭을잠그지않는다() throws Exception {
        BoundSql boundSql = configuration()
                .getMappedStatement(NAMESPACE + "selectActiveConversation")
                .getBoundSql(Map.of("userId", 1L));

        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("WHERE user_id = ?"));
        assertTrue(sql.contains("status = 'ACTIVE'"));
        assertFalse(sql.contains("FOR UPDATE"));
    }

    @Test
    void 질문시작은처리상태와질문순번을함께갱신한다() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("aiConversationId", 1L);
        parameters.put("userId", 2L);
        parameters.put("transcriptJson", "{\"schema_version\":1,\"turns\":[]}");
        parameters.put("processingStartedAt", LocalDateTime.now());

        BoundSql boundSql = configuration()
                .getMappedStatement(NAMESPACE + "markConversationProcessing")
                .getBoundSql(parameters);

        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("processing_status = 'PROCESSING'"));
        assertTrue(sql.contains("turn_count = turn_count + 1"));
        assertFalse(sql.contains("version"));
    }

    @Test
    void 질문완료는처리상태를초기화한다() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("aiConversationId", 1L);
        parameters.put("userId", 2L);
        parameters.put("conversationId", "conversation-1");
        parameters.put("transcriptJson", "{\"schema_version\":1,\"turns\":[]}");
        parameters.put("completedAt", LocalDateTime.now());

        BoundSql boundSql = configuration()
                .getMappedStatement(NAMESPACE + "completeConversationTurn")
                .getBoundSql(parameters);

        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains("processing_status = 'IDLE'"));
        assertTrue(sql.contains("processing_started_at = NULL"));
        assertFalse(sql.contains("turn_count = turn_count + 1"));
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/consultation/ConsultationMapper.xml";

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
