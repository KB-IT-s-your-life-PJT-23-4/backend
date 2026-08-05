package com.example.project.recipient.controller;

import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.example.project.gift.domain.TaxBracketVO;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.recipient.domain.RecipientVO;
import com.example.project.recipient.mapper.RecipientMapper;
import com.example.project.recipient.service.RecipientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RecipientControllerTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;
    private FakeRecipientMapper recipientMapper;
    private FakeGiftMapper giftMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        recipientMapper = new FakeRecipientMapper();
        giftMapper = new FakeGiftMapper();
        RecipientService recipientService = new RecipientService(recipientMapper, giftMapper);
        RecipientController controller = new RecipientController(recipientService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/fm/family는 가족을 등록하고 공통 성공 응답을 반환한다")
    void createRecipientApi() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(post("/api/gm/families")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyName": "홍길동",
                                  "relation": "LINEAL_DESCENDANT",
                                  "birthDate": "2010-01-02",
                                  "familyImg": "family.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(201, body.get("statusCode").asInt());
        assertEquals("/api/gm/families", body.get("path").asText());
        assertEquals("홍길동", body.at("/data/familyName").asText());
        assertEquals(OWNER_ID, recipientMapper.lastInserted.getUserId());
    }

    @Test
    @DisplayName("GET /api/gm/families는 로그인 사용자의 가족 목록만 반환한다")
    void getRecipientsApi() throws Exception {
        recipientMapper.add(recipient(10L, OWNER_ID, "본인가족"));
        recipientMapper.add(recipient(20L, OTHER_USER_ID, "타인가족"));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/families"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = body(result).get("data");
        assertEquals(1, data.size());
        assertEquals(10L, data.get(0).get("familyId").asLong());
    }

    @Test
    @DisplayName("GET /api/gm/families/{familyId}는 본인 가족 상세 정보를 반환한다")
    void getRecipientApi() throws Exception {
        recipientMapper.add(recipient(10L, OWNER_ID, "본인가족"));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/families/{familyId}", 10L))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(200, body.get("statusCode").asInt());
        assertEquals(10L, body.at("/data/familyId").asLong());
        assertEquals("본인가족", body.at("/data/familyName").asText());
    }

    @Test
    @DisplayName("수증자 상세 응답에는 가족 정보만 있고 Gift 집계와 Simulation 정보는 없다")
    void recipientDetailContainsOnlyFamilyData() throws Exception {
        recipientMapper.add(recipient(10L, OWNER_ID, "본인가족"));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/families/{familyId}", 10L))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = body(result).get("data");
        assertTrue(data.has("familyId"));
        assertTrue(data.has("familyName"));
        assertFalse(data.has("gifts"));
        assertFalse(data.has("giftList"));
        assertFalse(data.has("totalGiftAmount"));
        assertFalse(data.has("statusSummary"));
        assertFalse(data.has("latestGiftDate"));
        assertFalse(data.has("simulation"));
    }

    @Test
    @DisplayName("PATCH /api/gm/families/{familyId}는 본인 가족을 수정한다")
    void updateRecipientApi() throws Exception {
        recipientMapper.add(recipient(10L, OWNER_ID, "수정전"));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(patch("/api/gm/families/{familyId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyName": "수정후",
                                  "relation": "OTHER"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(203, body.get("statusCode").asInt());
        assertEquals("수정후", body.at("/data/familyName").asText());
        assertEquals("OTHER", body.at("/data/relation").asText());
    }

    @Test
    @DisplayName("DELETE /api/gm/families/{familyId}는 Gift가 없으면 가족을 삭제한다")
    void deleteRecipientApi() throws Exception {
        recipientMapper.add(recipient(10L, OWNER_ID, "삭제대상"));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(delete("/api/gm/families/{familyId}", 10L))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(204, body(result).get("statusCode").asInt());
        assertFalse(recipientMapper.recipients.containsKey(10L));
        assertEquals(1, giftMapper.countCallCount);
    }

    @Test
    @DisplayName("Gift가 존재하면 DELETE /api/gm/families/{familyId}는 409를 반환한다")
    void rejectDeleteWhenGiftExistsApi() throws Exception {
        recipientMapper.add(recipient(10L, OWNER_ID, "삭제대상"));
        giftMapper.giftCounts.put(10L, 1);
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(delete("/api/gm/families/{familyId}", 10L))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(409, body.get("statusCode").asInt());
        assertEquals("현재 데이터 상태와 요청이 충돌하였습니다", body.get("error").asText());
        assertTrue(recipientMapper.recipients.containsKey(10L));
    }

    @Test
    @DisplayName("force=true이면 Gift가 있어도 가족 삭제 요청을 수행한다")
    void forceDeleteRecipientApi() throws Exception {
        recipientMapper.add(recipient(10L, OWNER_ID, "강제삭제"));
        giftMapper.giftCounts.put(10L, 1);
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(delete("/api/gm/families/{familyId}", 10L)
                        .param("force", "true"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(204, body(result).get("statusCode").asInt());
        assertFalse(recipientMapper.recipients.containsKey(10L));
        assertEquals(0, giftMapper.countCallCount);
    }

    @Test
    @DisplayName("필수값이 누락된 가족 등록 요청은 400을 반환한다")
    void rejectMissingRequiredValueApi() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(post("/api/gm/families")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "relation": "OTHER",
                                  "birthDate": "2000-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(407, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("허용되지 않은 관계 값은 400을 반환한다")
    void rejectInvalidRelationApi() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(post("/api/gm/families")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyName": "홍길동",
                                  "relation": "PARENT",
                                  "birthDate": "2000-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(407, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("인증 사용자가 없으면 가족 API는 401을 반환한다")
    void rejectUnauthenticatedRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/gm/families"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals(401, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("요청서의 /gm/families 경로는 최신 Controller에 매핑되어 있지 않다")
    void pathWithoutApiPrefixIsNotMapped() throws Exception {
        mockMvc.perform(get("/gm/families"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("타 사용자 가족 상세 조회는 존재하지 않는 데이터처럼 404를 반환한다")
    void rejectOtherUsersRecipientApi() throws Exception {
        recipientMapper.add(recipient(10L, OTHER_USER_ID, "타인가족"));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/families/{familyId}", 10L))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(411, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("타 사용자 가족 수정은 404를 반환하고 데이터를 변경하지 않는다")
    void rejectOtherUsersRecipientUpdateApi() throws Exception {
        recipientMapper.add(recipient(10L, OTHER_USER_ID, "타인가족"));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(patch("/api/gm/families/{familyId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyName": "수정시도"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(411, body(result).get("statusCode").asInt());
        assertEquals("타인가족", recipientMapper.recipients.get(10L).getFamilyName());
    }

    @Test
    @DisplayName("존재하지 않는 familyId 조회는 404 공통 예외 응답을 반환한다")
    void missingRecipientApi() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/families/{familyId}", 999L))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(411, body.get("statusCode").asInt());
        assertEquals("/api/gm/families/999", body.get("path").asText());
        assertTrue(body.has("timestamp"));
        assertTrue(body.has("error"));
        assertFalse(body.has("data"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of())
        );
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private RecipientVO recipient(Long familyId, Long userId, String name) {
        RecipientVO recipient = new RecipientVO();
        recipient.setFamilyId(familyId);
        recipient.setUserId(userId);
        recipient.setFamilyName(name);
        recipient.setRelation("LINEAL_DESCENDANT");
        recipient.setBirthDate(LocalDate.of(2010, 1, 2));
        recipient.setFamilyImg("family.png");
        recipient.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        recipient.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return recipient;
    }

    private static class FakeRecipientMapper implements RecipientMapper {

        private final Map<Long, RecipientVO> recipients = new HashMap<>();
        private long sequence = 100L;
        private RecipientVO lastInserted;

        void add(RecipientVO recipient) {
            recipients.put(recipient.getFamilyId(), recipient);
        }

        @Override
        public RecipientVO selectRecipient(Long familyId, Long userId) {
            RecipientVO recipient = recipients.get(familyId);
            return recipient != null && recipient.getUserId().equals(userId) ? recipient : null;
        }

        @Override
        public List<RecipientVO> selectAllRecipient(Long userId) {
            return recipients.values().stream()
                    .filter(recipient -> recipient.getUserId().equals(userId))
                    .sorted(Comparator.comparing(RecipientVO::getFamilyId))
                    .toList();
        }

        @Override
        public void insertRecipient(RecipientVO recipient) {
            recipient.setFamilyId(++sequence);
            recipient.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            recipient.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            recipients.put(recipient.getFamilyId(), recipient);
            lastInserted = recipient;
        }

        @Override
        public void updateRecipient(RecipientVO recipient) {
            recipient.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
            recipients.put(recipient.getFamilyId(), recipient);
        }

        @Override
        public void deleteRecipient(Long familyId, Long userId) {
            RecipientVO recipient = selectRecipient(familyId, userId);
            if (recipient != null) {
                recipients.remove(familyId);
            }
        }
    }

    private static class FakeGiftMapper implements GiftMapper {

        private final Map<Long, Integer> giftCounts = new HashMap<>();
        private int countCallCount;

        @Override
        public int insertGift(GiftVO gift) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GiftVO selectGift(Long giftId, Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GiftVO> selectAllGift(Long familyId, Status status, Long userId) {
            return List.of();
        }

        @Override
        public int updateGift(Long giftId, Long amount, LocalDate giftDate, String memo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateGiftStatus(Long giftId, Status status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteGift(Long giftId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeductionVO> selectDeduction(
                Long familyId,
                Long userId,
                LocalDate windowStartDate,
                LocalDate baseDate,
                Long excludeGiftId
        ) {
            return List.of();
        }

        @Override
        public List<GiftVO> selectWindowGifts(
                Long familyId,
                Long userId,
                LocalDate windowStartDate,
                LocalDate baseDate
        ) {
            return List.of();
        }

        @Override
        public TaxBracketVO selectTaxBracket(LocalDate baseDate, Long taxableBase) {
            return null;
        }

        @Override
        public int countGiftByFamily(Long familyId) {
            countCallCount++;
            return giftCounts.getOrDefault(familyId, 0);
        }
    }
}
