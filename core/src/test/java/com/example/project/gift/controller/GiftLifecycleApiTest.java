package com.example.project.gift.controller;

import com.example.project.config.IntegrationTestWebClientConfig;
import com.example.project.config.RootConfig;
import com.example.project.config.ServletConfig;
import com.example.project.config.ocr.OCRWebClientConfig;
import com.example.project.consultation.config.WebClientConfig;
import com.example.project.security.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 증여 생명주기(등록 → 수정 → 상태 변경 → 삭제)의 실제 HTTP 상태와 응답 봉투를 확인한다.
 * API 명세서에 적을 값을 추측하지 않기 위한 테스트. 데이터는 롤백된다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(classes = {
                RootConfig.class,
                SecurityConfig.class,
                WebClientConfig.class,
                OCRWebClientConfig.class,
                IntegrationTestWebClientConfig.class
        }),
        @ContextConfiguration(classes = ServletConfig.class)
})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-integration-at-least-32-bytes",
        "ai.conversation.crypto.active-key-id=v1",
        "ai.conversation.crypto.key-v1=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "ai.conversation.crypto.key-v2="
})
@Transactional
class GiftLifecycleApiTest {

    private static final long USER_ID = 999_002L;
    private static final long FAMILY_ID = 999_201L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DataSource dataSource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(USER_ID), null, List.of()));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO user (user_id, password, user_name, phone, email) VALUES (?,?,?,?,?)",
                USER_ID, "x", "테스트부모2", "010-9999-0002", "lifecycle-test@example.com");
        jdbc.update("INSERT INTO family (family_id, user_id, family_name, relation, birth_date) VALUES (?,?,?,?,?)",
                FAMILY_ID, USER_ID, "자녀", "LINEAL_DESCENDANT", "2000-01-01");
    }

    private JsonNode log(String label, MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        System.out.println("=== " + label + " → HTTP " + result.getResponse().getStatus() + " ===");
        System.out.println(MAPPER.readTree(body).toPrettyString());
        return MAPPER.readTree(body);
    }

    @Test
    @DisplayName("등록 → 수정 → 상태 변경 → 삭제의 HTTP 상태와 봉투를 확인한다")
    void giftLifecycle() throws Exception {
        // 등록
        MvcResult created = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familyId\":" + FAMILY_ID + ",\"amount\":10000000,\"giftDate\":\"2026-07-01\",\"memo\":\"현금\"}"))
                .andReturn();
        JsonNode createdBody = log("POST /api/gm/gift", created);
        long giftId = createdBody.get("data").get("giftId").asLong();
        assertEquals(201, createdBody.get("statusCode").asInt(), "봉투 statusCode 는 201");
        assertEquals(200, created.getResponse().getStatus(), "HTTP 는 200 (ResponseEntity 미사용)");
        assertEquals("PLANNED", createdBody.get("data").get("status").asText(), "status 미지정 시 PLANNED");

        // 수정 — status 를 함께 보내도 무시되는지 확인
        MvcResult updated = mockMvc.perform(patch("/api/gm/gift/" + giftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20000000,\"memo\":\"수정됨\",\"status\":\"COMPLETED\"}"))
                .andReturn();
        JsonNode updatedBody = log("PATCH /api/gm/gift/{giftId}", updated);
        assertEquals(203, updatedBody.get("statusCode").asInt());
        assertEquals(20000000L, updatedBody.get("data").get("amount").asLong());
        assertEquals("PLANNED", updatedBody.get("data").get("status").asText(),
                "정보 수정으로 status 가 바뀌면 안 된다");

        // 상태 변경
        MvcResult statusChanged = mockMvc.perform(patch("/api/gm/gift/" + giftId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andReturn();
        JsonNode statusBody = log("PATCH /api/gm/gift/{giftId}/status", statusChanged);
        assertEquals("COMPLETED", statusBody.get("data").get("status").asText());

        // completed도 삭제가능
        MvcResult deleted = mockMvc.perform(delete("/api/gm/gift/" + giftId)).andReturn();
        JsonNode deleteBody = log("DELETE /api/gm/gift/{giftId} (COMPLETED)", deleted);
        assertEquals(200, deleted.getResponse().getStatus());
        assertEquals(204, deleteBody.get("statusCode").asInt(), "봉투 statusCode는 204(deleted)");

        MvcResult reread = mockMvc.perform(get("/api/gm/gift/" + giftId)).andReturn();
        log("GET /api/gm/gift/{giftId} (삭제 후)", reread);
        assertEquals(404, reread.getResponse().getStatus());
    }

    @Test
    @DisplayName("PLANNED 증여는 삭제된다")
    void deletePlannedGift() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familyId\":" + FAMILY_ID + ",\"amount\":5000000,\"giftDate\":\"2026-07-02\"}"))
                .andReturn();
        long giftId = MAPPER.readTree(created.getResponse().getContentAsString())
                .get("data").get("giftId").asLong();

        MvcResult deleted = mockMvc.perform(delete("/api/gm/gift/" + giftId)).andReturn();
        JsonNode body = log("DELETE /api/gm/gift/{giftId} (PLANNED)", deleted);
        assertEquals(200, deleted.getResponse().getStatus());
        assertEquals(204, body.get("statusCode").asInt(), "봉투 statusCode 는 204(DELETED)");
    }

    /**
     * 확정 이력 삭제가 10년 합산에 바로 반영되는지 확인한다.
     * 누적 증여액을 따로 적재하지 않는 구조라 별도 재계산 없이 원복되는 것이 정상이다.
     */
    @Test
    @DisplayName("확정 이력을 지우면 10년 합산 공제가 그만큼 되돌아온다")
    void deletingCompletedGiftRestoresDeduction() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familyId\":" + FAMILY_ID
                                + ",\"amount\":30000000,\"giftDate\":\"2026-07-03\",\"status\":\"COMPLETED\"}"))
                .andReturn();
        long giftId = MAPPER.readTree(created.getResponse().getContentAsString())
                .get("data").get("giftId").asLong();

        JsonNode before = deduction("삭제 전");
        assertEquals(30000000L, before.get("usedAmount").asLong(), "확정 증여가 누적 증여액에 잡힌다");

        mockMvc.perform(delete("/api/gm/gift/" + giftId)).andReturn();

        JsonNode after = deduction("삭제 후");
        assertEquals(0L, after.get("usedAmount").asLong(), "삭제한 증여는 합산에서 빠진다");
        assertEquals(before.get("deductionLimit").asLong(), after.get("remainingAmount").asLong(),
                "남은 공제가 한도 전액으로 돌아온다");
    }

    /**
     * 증여를 지우면 그 증여에 달린 리마인더 읽음 기록도 사라지는지 확인한다.
     * {@code reminder.gift_id} 의 ON DELETE CASCADE 에 기대는 부분이라 실제 DB 로 확인해야 한다.
     */
    @Test
    @DisplayName("증여를 지우면 리마인더 읽음 기록도 함께 사라진다")
    void deletingGiftRemovesReminderReads() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familyId\":" + FAMILY_ID
                                + ",\"amount\":10000000,\"giftDate\":\"2026-07-04\",\"status\":\"COMPLETED\"}"))
                .andReturn();
        long giftId = MAPPER.readTree(created.getResponse().getContentAsString())
                .get("data").get("giftId").asLong();

        mockMvc.perform(post("/api/rm/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftId\":" + giftId + ",\"type\":\"FILING_DEADLINE\"}"))
                .andReturn();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, reminderCount(jdbc, giftId), "읽음 처리로 reminder 행이 생긴다");

        MvcResult deleted = mockMvc.perform(delete("/api/gm/gift/" + giftId)).andReturn();
        assertEquals(200, deleted.getResponse().getStatus(), "리마인더가 붙어 있어도 삭제된다");

        assertEquals(0, reminderCount(jdbc, giftId), "CASCADE 로 읽음 기록이 함께 지워진다");
    }

    private int reminderCount(JdbcTemplate jdbc, long giftId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM reminder WHERE gift_id = ?", Integer.class, giftId);
    }

    private JsonNode deduction(String label) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/gm/deduction?familyId=" + FAMILY_ID)).andReturn();

        return log("GET /api/gm/deduction (" + label + ")", result).get("data").get(0);
    }

    @Test
    @DisplayName("금액이 0 이하면 INVALID_GIFT_AMOUNT")
    void invalidAmount() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familyId\":" + FAMILY_ID + ",\"amount\":0,\"giftDate\":\"2026-07-01\"}"))
                .andReturn();
        JsonNode body = log("POST /api/gm/gift (amount=0)", result);
        assertEquals(400, result.getResponse().getStatus());
        assertEquals(413, body.get("statusCode").asInt());
    }

    /**
     * 확정 이력은 오늘보다 뒤 날짜로 들어올 수 없다. 세 경로를 모두 막아야 의미가 있다 —
     * 하나라도 열려 있으면 합산에 안 잡히는 확정 건이 그대로 생긴다.
     */
    @Test
    @DisplayName("미래 날짜 확정 이력은 등록·수정·확정 모두 INVALID_GIFT_DATE")
    void futureDatedCompletedGiftIsRejected() throws Exception {
        String futureDate = LocalDate.now().plusDays(1).toString();
        String pastDate = LocalDate.now().minusDays(1).toString();

        // 1) COMPLETED 로 바로 등록
        MvcResult created = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familyId\":" + FAMILY_ID + ",\"amount\":1000000,\"giftDate\":\""
                                + futureDate + "\",\"status\":\"COMPLETED\"}"))
                .andReturn();
        JsonNode createdBody = log("POST /api/gm/gift (미래 COMPLETED)", created);
        assertEquals(400, created.getResponse().getStatus());
        assertEquals(421, createdBody.get("statusCode").asInt());

        // 2) 이미 확정된 건의 증여일을 미래로 수정
        long completedId = createGift("{\"familyId\":" + FAMILY_ID + ",\"amount\":1000000,\"giftDate\":\""
                + pastDate + "\",\"status\":\"COMPLETED\"}");
        MvcResult moved = mockMvc.perform(patch("/api/gm/gift/" + completedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"giftDate\":\"" + futureDate + "\"}"))
                .andReturn();
        JsonNode movedBody = log("PATCH /api/gm/gift/{giftId} (미래로 이동)", moved);
        assertEquals(400, moved.getResponse().getStatus());
        assertEquals(421, movedBody.get("statusCode").asInt());

        // 3) 증여일이 아직 안 온 계획을 확정
        long plannedId = createGift("{\"familyId\":" + FAMILY_ID + ",\"amount\":1000000,\"giftDate\":\""
                + futureDate + "\"}");
        MvcResult confirmed = mockMvc.perform(patch("/api/gm/gift/" + plannedId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andReturn();
        JsonNode confirmedBody = log("PATCH /api/gm/gift/{giftId}/status (미래 계획 확정)", confirmed);
        assertEquals(400, confirmed.getResponse().getStatus());
        assertEquals(421, confirmedBody.get("statusCode").asInt());
    }

    /** 계획(PLANNED)은 미래 날짜가 정상이다. 막혀 있으면 시뮬레이션에서 넘어온 계획을 저장할 수 없다. */
    @Test
    @DisplayName("PLANNED 는 미래 날짜로 등록된다")
    void futureDatedPlannedGiftIsAllowed() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familyId\":" + FAMILY_ID + ",\"amount\":1000000,\"giftDate\":\""
                                + LocalDate.now().plusYears(1) + "\"}"))
                .andReturn();
        JsonNode body = log("POST /api/gm/gift (미래 PLANNED)", created);

        assertEquals(201, body.get("statusCode").asInt());
        assertEquals("PLANNED", body.get("data").get("status").asText());
    }

    private long createGift(String body) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        return MAPPER.readTree(created.getResponse().getContentAsString())
                .get("data").get("giftId").asLong();
    }
}
