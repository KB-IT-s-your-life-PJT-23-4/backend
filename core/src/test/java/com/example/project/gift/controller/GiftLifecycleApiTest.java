package com.example.project.gift.controller;

import com.example.project.config.RootConfig;
import com.example.project.config.ServletConfig;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 증여 생명주기(등록 → 수정 → 상태 변경 → 삭제)의 실제 HTTP 상태와 응답 봉투를 확인한다.
 * API 명세서에 적을 값을 추측하지 않기 위한 테스트. 데이터는 롤백된다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(classes = {RootConfig.class, SecurityConfig.class}),
        @ContextConfiguration(classes = ServletConfig.class)
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

        // COMPLETED 는 삭제 불가 → 409
        MvcResult deleteRejected = mockMvc.perform(delete("/api/gm/gift/" + giftId)).andReturn();
        JsonNode rejectedBody = log("DELETE /api/gm/gift/{giftId} (COMPLETED)", deleteRejected);
        assertEquals(409, deleteRejected.getResponse().getStatus());
        assertEquals(409, rejectedBody.get("statusCode").asInt());
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
}
