package com.example.project.user.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserWithdrawalMapperXmlTest {

    private static final String NAMESPACE = "com.example.project.user.mapper.UserWithdrawalMapper.";

    @Test
    @DisplayName("가족 이미지 경로는 탈퇴 회원 소유 행만 조회한다")
    void findFamilyImagePathsByUserId() throws Exception {
        String sql = sql("findFamilyImagePaths");

        assertTrue(sql.contains("SELECT family_img FROM family"));
        assertTrue(sql.contains("WHERE user_id = ?"));
        assertTrue(sql.contains("family_img IS NOT NULL"));
    }

    @Test
    @DisplayName("AI 신고는 사용자 이벤트 참조와 사용자 ID를 모두 기준으로 삭제한다")
    void deleteAiSafetyReportsByEveryUserConnection() throws Exception {
        String byEventSql = sql("deleteAiSafetyReportsByTriggerEventUserId");
        String byUserSql = sql("deleteAiSafetyReportsByUserId");

        assertTrue(byEventSql.contains("DELETE FROM ai_safety_report"));
        assertTrue(byEventSql.contains("WHERE trigger_event_id IN"));
        assertTrue(byEventSql.contains("SELECT ai_consultation_event_id FROM ai_consultation_event WHERE user_id = ?"));
        assertTrue(byUserSql.contains("DELETE FROM ai_safety_report WHERE user_id = ?"));
    }

    @Test
    @DisplayName("AI 이벤트와 대화 및 상담 번호표는 탈퇴 회원 ID로 삭제한다")
    void deleteRemainingNonCascadeUserData() throws Exception {
        assertTrue(sql("deleteAiConsultationEventsByUserId")
                .contains("DELETE FROM ai_consultation_event WHERE user_id = ?"));
        assertTrue(sql("deleteAiConversationsByUserId")
                .contains("DELETE FROM ai_conversation WHERE user_id = ?"));
        assertTrue(sql("deleteTicketsByUserId")
                .contains("DELETE FROM kb_ticket WHERE user_id = ?"));
    }

    private String sql(String statementId) throws Exception {
        return configuration()
                .getMappedStatement(NAMESPACE + statementId)
                .getBoundSql(Map.of("userId", 7L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/user/UserWithdrawalMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }
        return configuration;
    }
}
