package com.example.project.admin.controller;

import com.example.project.admin.domain.DailySignupCount;
import com.example.project.admin.domain.LatestProductDataVersion;
import com.example.project.admin.domain.ProductTypeCount;
import com.example.project.admin.domain.SimulationDashboardCount;
import com.example.project.admin.mapper.AdminDashboardMapper;
import com.example.project.admin.service.AdminAuthorizationService;
import com.example.project.admin.service.AdminDashboardService;
import com.example.project.security.JwtProvider;
import com.example.project.security.SecurityConfig;
import com.example.project.security.TokenRevocationStore;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class AdminDashboardSecurityIntegrationTest {

    private static final String SECRET_KEY =
            "test-secret-key-for-admin-dashboard-must-be-at-least-32-bytes";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtProvider jwtProvider;
    private InMemoryUserMapper userMapper;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        jwtProvider = context.getBean(JwtProvider.class);
        userMapper = context.getBean(InMemoryUserMapper.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("대시보드 API는 JWT가 없으면 401, DB 역할이 USER이면 403을 반환한다")
    void protectDashboardWithAdminAuthorization() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());

        userMapper.save(user(1L, "USER"));
        String rootClaimToken = jwtProvider.createAccessToken("1", "ROOT");
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + rootClaimToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DEFAULT, MIDDLE, ROOT 관리자는 공통 응답 구조의 대시보드를 조회한다")
    void allowDashboardForEveryAdminRole() throws Exception {
        String[] roles = {"DEFAULT", "MIDDLE", "ROOT"};

        for (int index = 0; index < roles.length; index++) {
            long userId = index + 1L;
            userMapper.save(user(userId, roles[index]));
            String userClaimToken = jwtProvider.createAccessToken(String.valueOf(userId), "USER");

            var result = mockMvc.perform(get("/api/admin/dashboard")
                            .header("Authorization", "Bearer " + userClaimToken))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
            assertEquals(200, body.path("statusCode").asInt());
            assertEquals("/api/admin/dashboard", body.path("path").asText());
            assertEquals("database", body.path("data").path("source").asText());
            assertEquals(
                    "2026-08-06T15:00:00+09:00",
                    body.path("data").path("updatedAt").asText()
            );
            assertEquals(
                    "2026-07-31",
                    body.path("data").path("period").path("from").asText()
            );
            assertEquals(
                    "2026-08-06",
                    body.path("data").path("period").path("to").asText()
            );
            assertEquals(7, body.path("data").path("signups").path("trend").size());
            assertEquals(0.0, body.path("data").path("simulations").path("saveRate").asDouble());
            assertFalse(body.path("data").path("consultations").path("available").asBoolean());
            assertTrue(body.path("data").path("consultations").path("requests").isNull());
            assertFalse(body.path("data").path("products").path("available").asBoolean());

            String json = result.getResponse().getContentAsString().toLowerCase();
            assertFalse(json.contains("password"));
            assertFalse(json.contains("accesstoken"));
            assertFalse(json.contains("refreshtoken"));
            assertFalse(json.contains("authorization"));
            assertFalse(json.contains("phone"));
            assertFalse(json.contains("email"));
        }
    }

    private UserVO user(Long userId, String role) {
        UserVO user = new UserVO();
        user.setUserId(userId);
        user.setRole(role);
        return user;
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestWebConfig implements WebMvcConfigurer {

        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.stream()
                    .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                    .map(MappingJackson2HttpMessageConverter.class::cast)
                    .forEach(converter -> converter.getObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        }

        @Bean
        JwtProvider jwtProvider() {
            return new JwtProvider(SECRET_KEY, 60_000L, 120_000L);
        }

        @Bean
        TokenRevocationStore tokenRevocationStore() {
            return new TokenRevocationStore();
        }

        @Bean
        InMemoryUserMapper userMapper() {
            return new InMemoryUserMapper();
        }

        @Bean
        AdminAuthorizationService adminAuthorizationService(InMemoryUserMapper userMapper) {
            return new AdminAuthorizationService(userMapper);
        }

        @Bean
        AdminDashboardMapper adminDashboardMapper() {
            return new EmptyAdminDashboardMapper();
        }

        @Bean
        Clock applicationClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-06T06:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }

        @Bean
        AdminDashboardService adminDashboardService(
                AdminDashboardMapper mapper,
                Clock clock
        ) {
            return new AdminDashboardService(mapper, clock);
        }

        @Bean
        AdminDashboardController adminDashboardController(AdminDashboardService service) {
            return new AdminDashboardController(service);
        }
    }

    static class EmptyAdminDashboardMapper implements AdminDashboardMapper {

        @Override
        public List<DailySignupCount> selectDailySignupCounts(
                LocalDateTime startDateTime,
                LocalDateTime endDateTime
        ) {
            return List.of();
        }

        @Override
        public SimulationDashboardCount selectSimulationCounts(
                LocalDateTime startDateTime,
                LocalDateTime endDateTime
        ) {
            return null;
        }

        @Override
        public LatestProductDataVersion selectLatestCompletedProductDataVersion() {
            return null;
        }

        @Override
        public ProductTypeCount selectProductTypeCounts(Long productDataVersionId) {
            return null;
        }
    }

    static class InMemoryUserMapper implements UserMapper {

        private final Map<Long, UserVO> users = new ConcurrentHashMap<>();

        void save(UserVO user) {
            users.put(user.getUserId(), user);
        }

        @Override
        public UserVO findById(Long userId) {
            return users.get(userId);
        }

        @Override
        public UserVO findByEmail(String email) {
            return null;
        }

        @Override
        public int insert(UserVO userVO) {
            return 0;
        }

        @Override
        public int update(UserVO userVO) {
            return 0;
        }

        @Override
        public int deleteById(Long userId) {
            return 0;
        }
    }
}
