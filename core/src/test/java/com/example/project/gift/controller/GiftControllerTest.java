package com.example.project.gift.controller;

import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.example.project.gift.domain.TaxBracketVO;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.gift.service.GiftService;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GiftControllerTest {

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
        giftMapper = new FakeGiftMapper(recipientMapper);

        RecipientService recipientService = new RecipientService(recipientMapper, giftMapper);
        GiftService giftService = new GiftService(giftMapper, recipientService);
        GiftController controller = new GiftController(giftService);

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        recipientMapper.add(recipient(10L, OWNER_ID, "본인가족"));
        recipientMapper.add(recipient(20L, OTHER_USER_ID, "타인가족"));
        giftMapper.add(gift(101L, 10L, 1_000L, Status.PLANNED, LocalDate.of(2026, 1, 10)));
        giftMapper.add(gift(102L, 10L, 2_000L, Status.COMPLETED, LocalDate.of(2026, 2, 10)));
        giftMapper.add(gift(201L, 20L, 9_000L, Status.PLANNED, LocalDate.of(2026, 3, 10)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/gm/gift?familyId는 해당 가족의 Gift만 조회한다")
    void getGiftsByFamilyId() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/gift").param("familyId", "10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = body(result).get("data");
        assertEquals(2, data.size());
        assertEquals(10L, data.get(0).get("familyId").asLong());
        assertEquals(10L, data.get(1).get("familyId").asLong());
    }

    @Test
    @DisplayName("GET /api/gm/gift는 로그인 사용자가 소유한 Gift만 반환한다")
    void getOnlyCurrentUsersGifts() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/gift"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = body(result).get("data");
        assertEquals(2, data.size());
        assertTrue(containsGift(data, 101L));
        assertTrue(containsGift(data, 102L));
        assertFalse(containsGift(data, 201L));
    }

    @Test
    @DisplayName("GET /api/gm/gift의 status 조건은 상태가 일치하는 Gift만 반환한다")
    void filterGiftsByStatus() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/gift")
                        .param("familyId", "10")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = body(result).get("data");
        assertEquals(1, data.size());
        assertEquals(102L, data.get(0).get("giftId").asLong());
        assertEquals("COMPLETED", data.get(0).get("status").asText());
    }

    @Test
    @DisplayName("신고 안내는 최근 10년 과거 증여를 합산해 누진구간과 증분세액을 계산한다")
    void filingInfoUsesPreviousGiftsForProgressiveTax() throws Exception {
        recipientMapper.add(recipient(30L, OWNER_ID, "누진세율가족"));
        giftMapper.add(gift(
                301L,
                30L,
                500_000_000L,
                Status.COMPLETED,
                LocalDate.of(2025, 4, 10)
        ));
        giftMapper.add(gift(
                302L,
                30L,
                100_000_000L,
                Status.PLANNED,
                LocalDate.of(2026, 4, 10)
        ));
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/gift/{giftId}/filing-info", 302L))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = body(result).get("data");
        assertEquals(500_000_000L, data.get("priorGiftAmount").asLong());
        assertEquals(100_000_000L, data.get("taxableBase").asLong());
        assertEquals(0.3, data.get("taxRate").asDouble());
        assertEquals(25_000_000L, data.get("calculatedTax").asLong());
        assertEquals(750_000L, data.get("filingCredit").asLong());
        assertEquals(24_250_000L, data.get("payableTax").asLong());
    }

    @Test
    @DisplayName("타 사용자의 Gift 상세 조회는 404를 반환한다")
    void rejectOtherUsersGiftDetail() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/gift/{giftId}", 201L))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(412, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("존재하지 않는 familyId의 Gift 목록은 현재 구현상 빈 목록을 반환한다")
    void missingFamilyIdReturnsEmptyGiftList() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(get("/api/gm/gift").param("familyId", "999"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(200, body.get("statusCode").asInt());
        assertEquals(0, body.get("data").size());
    }

    @Test
    @DisplayName("본인 가족에는 Gift를 등록할 수 있다")
    void createGiftForOwnedFamily() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyId": 10,
                                  "amount": 3000,
                                  "giftDate": "2026-04-10",
                                  "status": "PLANNED",
                                  "memo": "테스트 증여"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(201, body.get("statusCode").asInt());
        assertEquals(10L, body.at("/data/familyId").asLong());
        assertEquals(3_000L, body.at("/data/amount").asLong());
    }

    @Test
    @DisplayName("타 사용자 가족에는 Gift를 등록할 수 없다")
    void rejectCreateGiftForOtherUsersFamily() throws Exception {
        authenticate(OWNER_ID);

        MvcResult result = mockMvc.perform(post("/api/gm/gift")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyId": 20,
                                  "amount": 3000,
                                  "giftDate": "2026-04-10"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(411, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("인증 사용자가 없으면 Gift API는 401을 반환한다")
    void rejectUnauthenticatedGiftRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/gm/gift"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals(401, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("요청서의 /gm 경로는 최신 GiftController에 매핑되어 있지 않다")
    void pathWithoutApiPrefixIsNotMapped() throws Exception {
        mockMvc.perform(get("/gm"))
                .andExpect(status().isNotFound());
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of())
        );
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private boolean containsGift(JsonNode gifts, long giftId) {
        for (JsonNode gift : gifts) {
            if (gift.get("giftId").asLong() == giftId) {
                return true;
            }
        }
        return false;
    }

    private RecipientVO recipient(Long familyId, Long userId, String name) {
        RecipientVO recipient = new RecipientVO();
        recipient.setFamilyId(familyId);
        recipient.setUserId(userId);
        recipient.setFamilyName(name);
        recipient.setRelation("LINEAL_DESCENDANT");
        recipient.setBirthDate(LocalDate.of(2010, 1, 2));
        return recipient;
    }

    private GiftVO gift(Long giftId, Long familyId, Long amount, Status status, LocalDate giftDate) {
        GiftVO gift = new GiftVO();
        gift.setGiftId(giftId);
        gift.setFamilyId(familyId);
        gift.setAmount(amount);
        gift.setStatus(status);
        gift.setGiftDate(giftDate);
        gift.setMemo("gift-" + giftId);
        return gift;
    }

    private static class FakeRecipientMapper implements RecipientMapper {

        private final Map<Long, RecipientVO> recipients = new HashMap<>();

        void add(RecipientVO recipient) {
            recipients.put(recipient.getFamilyId(), recipient);
        }

        Long ownerId(Long familyId) {
            RecipientVO recipient = recipients.get(familyId);
            return recipient == null ? null : recipient.getUserId();
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
                    .toList();
        }

        @Override
        public void insertRecipient(RecipientVO recipient) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRecipient(RecipientVO recipient) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRecipient(Long familyId, Long userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeGiftMapper implements GiftMapper {

        private final FakeRecipientMapper recipientMapper;
        private final Map<Long, GiftVO> gifts = new HashMap<>();
        private long sequence = 300L;

        private FakeGiftMapper(FakeRecipientMapper recipientMapper) {
            this.recipientMapper = recipientMapper;
        }

        void add(GiftVO gift) {
            gifts.put(gift.getGiftId(), gift);
        }

        @Override
        public int insertGift(GiftVO gift) {
            gift.setGiftId(++sequence);
            gifts.put(gift.getGiftId(), gift);
            return 1;
        }

        @Override
        public GiftVO selectGift(Long giftId, Long userId) {
            GiftVO gift = gifts.get(giftId);
            if (gift == null) {
                return null;
            }
            Long ownerId = recipientMapper.ownerId(gift.getFamilyId());
            return userId.equals(ownerId) ? gift : null;
        }

        @Override
        public List<GiftVO> selectAllGift(Long familyId, Status status, Long userId) {
            return gifts.values().stream()
                    .filter(gift -> userId.equals(recipientMapper.ownerId(gift.getFamilyId())))
                    .filter(gift -> familyId == null || familyId.equals(gift.getFamilyId()))
                    .filter(gift -> status == null || status == gift.getStatus())
                    .sorted(Comparator.comparing(GiftVO::getGiftDate).reversed()
                            .thenComparing(GiftVO::getGiftId, Comparator.reverseOrder()))
                    .toList();
        }

        @Override
        public int updateGift(Long giftId, Long amount, LocalDate giftDate, String memo) {
            GiftVO gift = gifts.get(giftId);
            if (gift == null) {
                return 0;
            }
            if (amount != null) {
                gift.setAmount(amount);
            }
            if (giftDate != null) {
                gift.setGiftDate(giftDate);
            }
            if (memo != null) {
                gift.setMemo(memo);
            }
            return 1;
        }

        @Override
        public int updateGiftStatus(Long giftId, Status status) {
            GiftVO gift = gifts.get(giftId);
            if (gift == null) {
                return 0;
            }
            gift.setStatus(status);
            return 1;
        }

        @Override
        public int deleteGift(Long giftId) {
            return gifts.remove(giftId) == null ? 0 : 1;
        }

        @Override
        public List<DeductionVO> selectDeduction(
                Long familyId,
                Long userId,
                LocalDate windowStartDate,
                LocalDate baseDate,
                Long excludeGiftId
        ) {
            RecipientVO recipient = recipientMapper.selectRecipient(familyId, userId);
            if (recipient == null) {
                return List.of();
            }

            List<GiftVO> windowGifts = gifts.values().stream()
                    .filter(gift -> familyId.equals(gift.getFamilyId()))
                    .filter(gift -> excludeGiftId == null || !excludeGiftId.equals(gift.getGiftId()))
                    .filter(gift -> !gift.getGiftDate().isBefore(windowStartDate))
                    .filter(gift -> !gift.getGiftDate().isAfter(baseDate))
                    .toList();
            DeductionVO deduction = new DeductionVO();
            deduction.setFamilyId(familyId);
            deduction.setFamilyName(recipient.getFamilyName());
            deduction.setRelation(recipient.getRelation());
            deduction.setBirthDate(recipient.getBirthDate());
            deduction.setDeductionLimit(50_000_000L);
            deduction.setUsedAmount(windowGifts.stream()
                    .filter(gift -> gift.getStatus() == Status.COMPLETED)
                    .mapToLong(GiftVO::getAmount)
                    .sum());
            deduction.setPlannedAmount(windowGifts.stream()
                    .filter(gift -> gift.getStatus() == Status.PLANNED)
                    .mapToLong(GiftVO::getAmount)
                    .sum());
            deduction.setAggregatedCount((int) windowGifts.stream()
                    .filter(gift -> gift.getStatus() == Status.COMPLETED)
                    .count());
            return List.of(deduction);
        }

        @Override
        public List<GiftVO> selectWindowGifts(
                Long familyId,
                Long userId,
                LocalDate windowStartDate,
                LocalDate baseDate
        ) {
            return gifts.values().stream()
                    .filter(gift -> userId.equals(
                            recipientMapper.ownerId(gift.getFamilyId())))
                    .filter(gift -> familyId.equals(gift.getFamilyId()))
                    .filter(gift -> gift.getStatus() == Status.COMPLETED)
                    .filter(gift -> !gift.getGiftDate().isBefore(windowStartDate))
                    .filter(gift -> gift.getGiftDate().isBefore(baseDate))
                    .sorted(Comparator.comparing(GiftVO::getGiftDate)
                            .thenComparing(GiftVO::getGiftId))
                    .toList();
        }

        @Override
        public Long selectDeductionLimit(String relation, boolean minor, LocalDate baseDate) {
            return null;
        }

        @Override
        public TaxBracketVO selectTaxBracket(LocalDate baseDate, Long taxableBase) {
            TaxBracketVO bracket = new TaxBracketVO();
            if (taxableBase <= 100_000_000L) {
                bracket.setTaxRate(new BigDecimal("0.10"));
                bracket.setProgressiveDeduction(0L);
            } else if (taxableBase <= 500_000_000L) {
                bracket.setTaxRate(new BigDecimal("0.20"));
                bracket.setProgressiveDeduction(10_000_000L);
            } else if (taxableBase <= 1_000_000_000L) {
                bracket.setTaxRate(new BigDecimal("0.30"));
                bracket.setProgressiveDeduction(60_000_000L);
            } else if (taxableBase <= 3_000_000_000L) {
                bracket.setTaxRate(new BigDecimal("0.40"));
                bracket.setProgressiveDeduction(160_000_000L);
            } else {
                bracket.setTaxRate(new BigDecimal("0.50"));
                bracket.setProgressiveDeduction(460_000_000L);
            }
            return bracket;
        }

        @Override
        public int countGiftByFamily(Long familyId) {
            return (int) gifts.values().stream()
                    .filter(gift -> familyId.equals(gift.getFamilyId()))
                    .count();
        }

        @Override
        public com.example.project.gift.domain.SimulationGiftSource selectSimulationGiftSource(
                Long simulationId,
                Long userId
        ) {
            return null;
        }

        @Override
        public List<com.example.project.simulation.domain.SimulationTrancheRecord> selectTranchesByResultId(
                Long simulResultId
        ) {
            return List.of();
        }

        @Override
        public int countGiftBySimulResultId(Long simulResultId) {
            return 0;
        }
    }
}
