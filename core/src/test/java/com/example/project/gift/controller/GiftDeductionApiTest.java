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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.sql.DataSource;
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
        @ContextConfiguration(classes = {RootConfig.class, SecurityConfig.class}),
        @ContextConfiguration(classes = ServletConfig.class)
})
@Transactional
class GiftDeductionApiTest {

    private static final long USER_ID = 999_001L;
    private static final long ADULT_FAMILY_ID = 999_101L;
    private static final long MINOR_FAMILY_ID = 999_102L;
    private static final long NO_GIFT_FAMILY_ID = 999_103L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DataSource dataSource;

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

        jdbc.update("INSERT INTO user (user_id, password, user_name, phone, email) VALUES (?,?,?,?,?)",
                USER_ID, "x", "테스트부모", "010-9999-0001", "deduction-test@example.com");

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

        jdbc.update("INSERT INTO family (family_id, user_id, family_name, relation, birth_date) VALUES (?,?,?,?,?)",
                ADULT_FAMILY_ID, USER_ID, "성년자녀", "LINEAL_DESCENDANT", "1998-03-02");
        jdbc.update("INSERT INTO family (family_id, user_id, family_name, relation, birth_date) VALUES (?,?,?,?,?)",
                MINOR_FAMILY_ID, USER_ID, "미성년자녀", "LINEAL_DESCENDANT", "2012-05-20");
        jdbc.update("INSERT INTO family (family_id, user_id, family_name, relation, birth_date) VALUES (?,?,?,?,?)",
                NO_GIFT_FAMILY_ID, USER_ID, "이력없음", "LINEAL_DESCENDANT", "2020-01-01");

        // 성년: 확정 3,000만 + 창 밖 1,000만(제외) + 계획 500만 + 취소 900만(제외)
        insertGift(ADULT_FAMILY_ID, 30_000_000L, "2020-04-01", "COMPLETED");
        insertGift(ADULT_FAMILY_ID, 10_000_000L, "2014-01-01", "COMPLETED");
        insertGift(ADULT_FAMILY_ID, 5_000_000L, "2026-01-10", "PLANNED");
        insertGift(ADULT_FAMILY_ID, 9_000_000L, "2021-01-01", "CANCELLED");

        // 미성년: 한도 2,000만을 넘긴 2,500만
        insertGift(MINOR_FAMILY_ID, 25_000_000L, "2019-06-01", "COMPLETED");
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

        assertEquals(3, data.size());

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
        assertEquals("2030-04-01", adult.get("nextRenewalDate").asText(), "2020-04-01 + 10년");

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

    @Test
    @DisplayName("familyId 로 한 명만 조회한다")
    void deductionForSingleFamily() throws Exception {
        JsonNode data = requestDeduction("?familyId=" + MINOR_FAMILY_ID);

        assertEquals(1, data.size());
        assertEquals(MINOR_FAMILY_ID, data.get(0).get("familyId").asLong());
    }
}
