package com.example.project.gift.controller;

import com.example.project.config.IntegrationTestWebClientConfig;
import com.example.project.config.AdminAccessStreamConfig;
import com.example.project.config.RootConfig;
import com.example.project.config.ServletConfig;
import com.example.project.config.ocr.OCRWebClientConfig;
import com.example.project.consultation.config.WebClientConfig;
import com.example.project.security.SecurityConfig;
import com.example.project.recipient.domain.RecipientVO;
import com.example.project.user.crypto.UserPiiProtectionService;
import com.example.project.user.domain.UserVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/gm/deduction 을 실제 컨텍스트·실제 DB 로 확인한다.
 * 넣은 데이터는 @Transactional 롤백으로 사라지므로 개발 DB 에 남지 않는다.
 * 실행에는 로컬 MySQL(miriZoom)이 떠 있어야 한다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextHierarchy({
        // WebConfig.getRootConfigClasses() 와 동일하게 맞춘다. SecurityConfig 에 PasswordEncoder 가 있다.
        @ContextConfiguration(classes = {
                RootConfig.class,
                SecurityConfig.class,
                WebClientConfig.class,
                OCRWebClientConfig.class,
                IntegrationTestWebClientConfig.class,
                AdminAccessStreamConfig.class
        }),
        @ContextConfiguration(classes = ServletConfig.class)
})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-integration-at-least-32-bytes",
        "ai.conversation.crypto.active-key-id=v1",
        "ai.conversation.crypto.key-v1=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "ai.conversation.crypto.key-v2=",
        "pii.search.hmac-key-v1=QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo1Njc4OTA="
})
@Transactional
class GiftDeductionApiTest {

    private static final long USER_ID = 999_001L;
    private static final long ADULT_FAMILY_ID = 999_101L;
    private static final long MINOR_FAMILY_ID = 999_102L;
    private static final long NO_GIFT_FAMILY_ID = 999_103L;

    /** 한도를 넘기지 않았고, 증여가 창에서 빠지기 전에 성년이 되는 수증자. */
    private static final long YOUNG_MINOR_FAMILY_ID = 999_104L;

    /** 확정 이력을 오늘보다 뒤 날짜로 등록한 수증자. */
    private static final long FUTURE_GIFT_FAMILY_ID = 999_105L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserPiiProtectionService piiProtectionService;

    private MockMvc mockMvc;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // 실제 앱은 WebConfig.getServletFilters() 로 이 필터를 탄다. 없으면 한글이 깨져 보인다.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
        jdbc = new JdbcTemplate(dataSource);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(USER_ID), null, List.of()));

        insertUser(USER_ID, "deduction-test@example.com", "010-9999-0001", "Test User");

        jdbc.update("DELETE FROM gift_deduction_limit WHERE relation = ?",
                "LINEAL_DESCENDANT");
        jdbc.update("""
                        INSERT INTO gift_deduction_limit
                            (effective_from, effective_to, relation, is_minor, deduction_limit)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                "2000-01-01", null, "LINEAL_DESCENDANT", false, 50_000_000L);
        jdbc.update("""
                        INSERT INTO gift_deduction_limit
                            (effective_from, effective_to, relation, is_minor, deduction_limit)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                "2000-01-01", null, "LINEAL_DESCENDANT", true, 20_000_000L);

        insertFamily(ADULT_FAMILY_ID, "Adult", LocalDate.of(1998, 3, 2));
        insertFamily(MINOR_FAMILY_ID, "Minor", LocalDate.of(2012, 5, 20));
        insertFamily(NO_GIFT_FAMILY_ID, "No Gift", LocalDate.of(2020, 1, 1));
        insertFamily(YOUNG_MINOR_FAMILY_ID, "Young Minor", LocalDate.of(2015, 8, 1));
        insertFamily(FUTURE_GIFT_FAMILY_ID, "Future Gift", LocalDate.of(1995, 1, 1));

        // 성년: 확정 3,000만 + 창 밖 1,000만(제외) + 계획 500만 + 취소 900만(제외)
        insertGift(ADULT_FAMILY_ID, 30_000_000L, "2020-04-01", "COMPLETED");
        insertGift(ADULT_FAMILY_ID, 10_000_000L, "2014-01-01", "COMPLETED");
        insertGift(ADULT_FAMILY_ID, 5_000_000L, "2026-01-10", "PLANNED");
        insertGift(ADULT_FAMILY_ID, 9_000_000L, "2021-01-01", "CANCELLED");

        // 미성년: 한도 2,000만을 넘긴 2,500만
        insertGift(MINOR_FAMILY_ID, 25_000_000L, "2019-06-01", "COMPLETED");

        // 성년 전환이 먼저 오는 미성년: 한도 2,000만 안쪽인 1,700만. 같은 날 2건으로 넣는다.
        insertGift(YOUNG_MINOR_FAMILY_ID, 15_000_000L, "2026-08-01", "COMPLETED");
        insertGift(YOUNG_MINOR_FAMILY_ID, 2_000_000L, "2026-08-01", "COMPLETED");

        // 오늘보다 뒤 날짜로 등록된 확정 이력. 등록 자체는 막히지 않는다.
        insertGift(FUTURE_GIFT_FAMILY_ID, 40_000_000L,
                LocalDate.now().plusYears(1).toString(), "COMPLETED");
    }

    private void insertUser(long userId, String email, String phone, String name) {
        UserVO user = new UserVO();
        user.setEmail(email);
        user.setPhone(phone);
        user.setUserName(name);
        piiProtectionService.protect(user);
        jdbc.update("""
                INSERT INTO user
                    (user_id, password, user_name_encrypted, phone_encrypted, phone_hmac,
                     email_encrypted, email_hmac)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, userId, "x", user.getUserNameEncrypted(), user.getPhoneEncrypted(),
                user.getPhoneHmac(), user.getEmailEncrypted(), user.getEmailHmac());
    }

    private void insertFamily(long familyId, String name, LocalDate birthDate) {
        RecipientVO family = new RecipientVO();
        family.setFamilyName(name);
        family.setBirthDate(birthDate);
        piiProtectionService.protect(family);
        jdbc.update("""
                INSERT INTO family
                    (family_id, user_id, family_name_encrypted, relation, birth_date_encrypted)
                VALUES (?, ?, ?, 'LINEAL_DESCENDANT', ?)
                """, familyId, USER_ID, family.getFamilyNameEncrypted(),
                family.getBirthDateEncrypted());
    }

    private void insertGift(long familyId, long amount, String giftDate, String status) {
        jdbc.update("INSERT INTO gift (family_id, amount, gift_date, status) VALUES (?,?,?,?)",
                familyId, amount, giftDate, status);
    }

    private JsonNode requestDeduction(String query) throws Exception {
        String body = mockMvc.perform(get("/api/gm/deduction" + query))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("=== GET /api/gm/deduction" + query + " ===");
        System.out.println(MAPPER.readTree(body).toPrettyString());

        return MAPPER.readTree(body).get("data");
    }

    @Test
    @DisplayName("공제 현황 응답을 실제 JSON 으로 확인한다")
    void deductionResponse() throws Exception {
        JsonNode data = requestDeduction("");

        assertEquals(5, data.size());

        // 성년 자녀: 한도 5,000만, 사용 3,000만(창 밖·취소 제외), 잔여 2,000만
        JsonNode adult = data.get(0);
        assertEquals(ADULT_FAMILY_ID, adult.get("familyId").asLong());
        assertFalse(adult.get("minor").asBoolean(), "1998년생은 성년");
        assertEquals(50_000_000L, adult.get("deductionLimit").asLong());
        assertEquals(30_000_000L, adult.get("usedAmount").asLong(), "창 밖·취소 건은 빠져야 한다");
        assertEquals(5_000_000L, adult.get("plannedAmount").asLong());
        assertEquals(20_000_000L, adult.get("remainingAmount").asLong());
        assertEquals(15_000_000L, adult.get("remainingAmountIfPlanned").asLong());
        assertEquals(1, adult.get("aggregatedCount").asInt());
        // 창 조건이 소급 10년 되는 날을 포함하므로(gift_date >= baseDate - 10년)
        // 2030-04-01 까지는 아직 합산에 들어간다. 여력이 실제로 늘어나는 첫 날은 그 다음 날이다.
        assertEquals("2030-04-02", adult.get("nextRenewalDate").asText(), "2020-04-01 + 10년 + 1일");

        // 미성년 자녀: 한도를 넘겨도 잔여는 음수가 아니라 0
        JsonNode minor = data.get(1);
        assertTrue(minor.get("minor").asBoolean(), "2012년생은 미성년");
        assertEquals(20_000_000L, minor.get("deductionLimit").asLong());
        assertEquals(25_000_000L, minor.get("usedAmount").asLong());
        assertEquals(0L, minor.get("remainingAmount").asLong());

        // 증여 이력 없음: 한도 전액이 남고 갱신일은 없다
        JsonNode noGift = data.get(2);
        assertEquals(0L, noGift.get("usedAmount").asLong());
        assertEquals(20_000_000L, noGift.get("remainingAmount").asLong());
        assertEquals(0, noGift.get("aggregatedCount").asInt());
        assertTrue(noGift.get("nextRenewalDate") == null || noGift.get("nextRenewalDate").isNull(),
                "확정 증여가 없으면 갱신일도 없다");
    }

    /**
     * 갱신일을 "가장 오래된 증여가 창에서 빠지는 날"로만 잡으면 틀리는 경우.
     * 미성년 수증자는 성년이 되는 날 한도가 2,000만 → 5,000만으로 올라가고, 그게 더 이를 수 있다.
     */
    @Test
    @DisplayName("성년이 되는 날이 증여가 빠지는 날보다 이르면 그날이 갱신일이다")
    void renewalFollowsAdultTransition() throws Exception {
        JsonNode row = requestDeduction("?familyId=" + YOUNG_MINOR_FAMILY_ID).get(0);

        assertTrue(row.get("minor").asBoolean(), "2015년생은 미성년");
        assertEquals(20_000_000L, row.get("deductionLimit").asLong());
        assertEquals(17_000_000L, row.get("usedAmount").asLong());
        assertEquals(3_000_000L, row.get("remainingAmount").asLong(), "아직 한도를 넘기지 않았다");

        // 증여가 빠지는 날은 2036-08-02, 성년이 되는 날은 2034-08-01. 이른 쪽이 갱신일이다.
        assertEquals("2034-08-01", row.get("nextRenewalDate").asText(), "2015-08-01 + 19년");

        // 성년 한도 5,000만 - 창에 남은 1,700만 = 3,300만. 지금 여력 300만과의 차이가 늘어나는 몫이다.
        assertEquals(30_000_000L, row.get("renewalAmount").asLong());

        // 성년 전환은 특정 증여가 일으키는 게 아니라 창 안 가장 오래된 증여를 알림 키로 쓴다.
        assertFalse(row.get("renewalGiftId").isNull(), "리마인더 식별용 giftId 는 있어야 한다");
    }

    /**
     * 미래 날짜로 등록된 확정 이력은 합산에서 빠진다. 창 조건이 {@code gift_date <= 오늘} 이라
     * 아직 일어나지 않은 증여가 "이미 증여한 금액"에 잡히지 않는다.
     *
     * <p>다만 등록 자체는 막히지 않고 증여 목록에는 그대로 나오므로,
     * 화면에는 이력으로 보이는데 누적 증여액에는 안 잡히는 상태가 된다.
     */
    @Test
    @DisplayName("오늘보다 뒤 날짜의 확정 이력은 10년 합산에 들어가지 않는다")
    void futureDatedGiftIsExcluded() throws Exception {
        JsonNode row = requestDeduction("?familyId=" + FUTURE_GIFT_FAMILY_ID).get(0);

        assertEquals(0L, row.get("usedAmount").asLong(), "미래 증여는 누적 증여액에 안 잡힌다");
        assertEquals(0, row.get("aggregatedCount").asInt());
        assertEquals(50_000_000L, row.get("remainingAmount").asLong(), "한도 전액이 남는다");
        assertTrue(row.get("nextRenewalDate") == null || row.get("nextRenewalDate").isNull(),
                "합산에 들어간 증여가 없으면 갱신할 것도 없다");
    }

    @Test
    @DisplayName("familyId 로 한 명만 조회한다")
    void deductionForSingleFamily() throws Exception {
        JsonNode data = requestDeduction("?familyId=" + MINOR_FAMILY_ID);

        assertEquals(1, data.size());
        assertEquals(MINOR_FAMILY_ID, data.get(0).get("familyId").asLong());
    }
}
