package com.example.project.user.integration;

import com.example.project.config.IntegrationTestWebClientConfig;
import com.example.project.config.AdminAccessStreamConfig;
import com.example.project.config.RootConfig;
import com.example.project.config.ocr.OCRWebClientConfig;
import com.example.project.consultation.config.WebClientConfig;
import com.example.project.security.SecurityConfig;
import com.example.project.user.domain.UserVO;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserSignupRequest;
import com.example.project.user.dto.request.UserUpdateRequest;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RootConfig.class,
        SecurityConfig.class,
        WebClientConfig.class,
        OCRWebClientConfig.class,
        IntegrationTestWebClientConfig.class,
        AdminAccessStreamConfig.class
})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-integration-at-least-32-bytes",
        "ai.conversation.crypto.active-key-id=v1",
        "ai.conversation.crypto.key-v1=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "ai.conversation.crypto.key-v2="
})
@Transactional
class UserDatabaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("회원가입, 조회, 수정 시 최종 DDL의 User 컬럼이 MyBatis로 연동된다")
    void userColumnsAreMappedThroughSignupReadAndUpdate() {
        long suffix = Math.floorMod(System.nanoTime(), 100_000_000L);
        String email = "user-db-" + suffix + "@example.com";
        String phone = String.format("010%08d", suffix);

        UserDTO created = userService.signup(new UserSignupRequest(
                email,
                "password123!",
                "테스트회원",
                LocalDate.of(1990, 1, 1),
                phone,
                "profile.png"
        ));

        assertNotNull(created.userId());
        assertEquals(email, created.email());
        assertEquals(LocalDate.of(1990, 1, 1), created.birthDate());
        assertEquals(phone, created.phone());
        assertEquals("USER", created.role());
        assertEquals("profile.png", created.img());

        UserVO foundById = userMapper.findById(created.userId());
        UserVO foundByEmail = userMapper.findByEmail(email);

        assertNotNull(foundById);
        assertNotNull(foundByEmail);
        assertEquals(created.userId(), foundByEmail.getUserId());
        assertNotEquals("password123!", foundById.getPassword());
        assertNotNull(foundById.getCreatedAt());
        assertNotNull(foundById.getUpdatedAt());

        String updatedEmail = "updated-db-" + suffix + "@example.com";
        String updatedPhone = String.format("011%08d", suffix);
        UserDTO updated = userService.updateProfile(
                created.userId(),
                new UserUpdateRequest(
                        updatedEmail,
                        "수정회원",
                        LocalDate.of(1991, 2, 2),
                        updatedPhone,
                        "updated.png"
                )
        );

        assertEquals(updatedEmail, updated.email());
        assertEquals("수정회원", updated.name());
        assertEquals(LocalDate.of(1991, 2, 2), updated.birthDate());
        assertEquals(updatedPhone, updated.phone());
        assertEquals("USER", updated.role());
        assertEquals("updated.png", updated.img());

        UserVO reloaded = userMapper.findById(created.userId());
        assertEquals(updatedEmail, reloaded.getEmail());
        assertEquals(updatedPhone, reloaded.getPhone());
        assertEquals("updated.png", reloaded.getImg());
    }

    @Test
    @DisplayName("회원 탈퇴는 명시적 정리 대상과 CASCADE 회원 소유 데이터를 모두 삭제한다")
    void withdrawalPurgesExplicitAndCascadeOwnedData() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Assumptions.assumeTrue(
                hasCurrentWithdrawalSchema(jdbc),
                "회원 파기 통합 테스트에는 저장소의 0.8.0 DDL이 적용되어야 합니다."
        );
        long suffix = Math.floorMod(System.nanoTime(), 100_000_000L);
        UserDTO user = userService.signup(new UserSignupRequest(
                "withdrawal-" + suffix + "@example.com",
                "password123!",
                "가상회원",
                LocalDate.of(1990, 1, 1),
                String.format("019%08d", suffix),
                "profile.png"
        ));

        jdbc.update("""
                INSERT INTO family (user_id, family_name, relation, birth_date, family_img)
                VALUES (?, ?, 'OTHER', ?, ?)
                """, user.userId(), "가상가족", LocalDate.of(2010, 1, 1), "family.png");
        Long familyId = jdbc.queryForObject(
                "SELECT family_id FROM family WHERE user_id = ?",
                Long.class,
                user.userId()
        );

        String versionCode = "withdrawal-" + suffix;
        jdbc.update("""
                INSERT INTO kb_product_data_version (version_code, data_date, status, completed_at)
                VALUES (?, CURRENT_DATE, 'COMPLETED', CURRENT_TIMESTAMP)
                """, versionCode);
        Long dataVersionId = jdbc.queryForObject(
                "SELECT kb_product_data_version_id FROM kb_product_data_version WHERE version_code = ?",
                Long.class,
                versionCode
        );
        jdbc.update("""
                INSERT INTO simulation (
                    kb_product_data_version_id, family_id, requested_amount, status,
                    tax_payment_method, investment_period_months, as_of_date, gift_date,
                    investment_end_date, calculation_version, formula_version, version,
                    previous_gift_amount, deduction_limit, expired_at
                ) VALUES (?, ?, 1000000, 'DRAFT', 'RECIPIENT_PAYS', 12,
                          '2026-08-11', '2026-08-11', '2027-08-11',
                          'test-calculation', 'test-formula', 1, 0, 1000000,
                          CURRENT_TIMESTAMP + INTERVAL 1 DAY)
                """, dataVersionId, familyId);
        jdbc.update("""
                INSERT INTO gift (family_id, amount, gift_date, status, memo)
                VALUES (?, 1000000, '2026-08-11', 'PLANNED', '가상 증여')
                """, familyId);
        Long giftId = jdbc.queryForObject(
                "SELECT gift_id FROM gift WHERE family_id = ?",
                Long.class,
                familyId
        );
        jdbc.update("""
                INSERT INTO reminder (gift_id, reminder_type, target_date)
                VALUES (?, 'FILING_DEADLINE', '2026-09-01')
                """, giftId);

        String conversationId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO ai_conversation (
                    conversation_id, user_id, transcript_json, turn_count
                ) VALUES (?, ?, CAST(? AS JSON), 1)
                """, conversationId, user.userId(), "{\"turns\":[{\"question\":\"가상 질문\"}]}");
        Long aiConversationId = jdbc.queryForObject(
                "SELECT ai_conversation_id FROM ai_conversation WHERE user_id = ?",
                Long.class,
                user.userId()
        );
        String requestId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO ai_consultation_event (
                    ai_conversation_id, conversation_id, request_id, turn_no, user_id,
                    intent, response_status, question_excerpt
                ) VALUES (?, ?, ?, 1, ?, 'other', 'COMPLETED', '가상 질문 일부')
                """, aiConversationId, conversationId, requestId, user.userId());
        Long eventId = jdbc.queryForObject(
                "SELECT ai_consultation_event_id FROM ai_consultation_event WHERE request_id = ?",
                Long.class,
                requestId
        );
        jdbc.update("""
                INSERT INTO ai_safety_report (
                    report_key, report_type, user_id, trigger_event_id, occurrence_count
                ) VALUES (?, 'JAILBREAK', NULL, ?, 1)
                """, "withdrawal-event-" + suffix, eventId);
        jdbc.update("""
                INSERT INTO ai_safety_report (
                    report_key, report_type, user_id, trigger_event_id, occurrence_count
                ) VALUES (?, 'OTHER_THRESHOLD', ?, ?, 1)
                """, "withdrawal-user-" + suffix, user.userId(), eventId);

        String branchName = "가상지점-" + suffix;
        String deskCode = "W" + suffix;
        jdbc.update("INSERT INTO kb_branch (branch_name, is_active) VALUES (?, 1)", branchName);
        Long branchId = jdbc.queryForObject(
                "SELECT branch_id FROM kb_branch WHERE branch_name = ?",
                Long.class,
                branchName
        );
        jdbc.update("""
                INSERT INTO kb_desk_type (
                    desk_type_code, desk_type_name, prefix, is_operating
                ) VALUES (?, '가상창구', 'W', 1)
                """, deskCode);
        jdbc.update("""
                INSERT INTO kb_ticket (
                    branch_id, desk_type_code, service_type, ticket_number,
                    user_id, status, business_date
                ) VALUES (?, ?, 'VIRTUAL_SERVICE', 1, ?, 'WAITING', CURRENT_DATE)
                """, branchId, deskCode, user.userId());

        userService.deleteUser(user.userId());

        assertEquals(0, count(jdbc, "user", "user_id", user.userId()));
        assertEquals(0, count(jdbc, "family", "user_id", user.userId()));
        assertEquals(0, count(jdbc, "simulation", "family_id", familyId));
        assertEquals(0, count(jdbc, "gift", "family_id", familyId));
        assertEquals(0, count(jdbc, "reminder", "gift_id", giftId));
        assertEquals(0, count(jdbc, "ai_safety_report", "trigger_event_id", eventId));
        assertEquals(0, count(jdbc, "ai_safety_report", "user_id", user.userId()));
        assertEquals(0, count(jdbc, "ai_consultation_event", "user_id", user.userId()));
        assertEquals(0, count(jdbc, "ai_conversation", "user_id", user.userId()));
        assertEquals(0, count(jdbc, "kb_ticket", "user_id", user.userId()));
    }

    private int count(JdbcTemplate jdbc, String table, String column, Long id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }

    private boolean hasCurrentWithdrawalSchema(JdbcTemplate jdbc) {
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'ai_conversation',
                      'ai_consultation_event',
                      'ai_safety_report',
                      'kb_ticket'
                  )
                """, Integer.class);
        return tableCount != null && tableCount == 4;
    }
}
