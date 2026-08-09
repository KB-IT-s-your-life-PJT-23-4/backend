package com.example.project.admin.auth.controller;

import com.example.project.admin.auth.mapper.AdminAuthMapper;
import com.example.project.admin.auth.service.AdminAuthorizationService;
import com.example.project.security.JwtProvider;
import com.example.project.security.SecurityConfig;
import com.example.project.security.TokenRevocationStore;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSecurityIntegrationTest {

    private static final String SECRET_KEY =
            "test-secret-key-for-admin-security-must-be-at-least-32-bytes";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtProvider jwtProvider;
    private InMemoryUserMapper userMapper;
    private TokenRevocationStore tokenRevocationStore;

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
        tokenRevocationStore = context.getBean(TokenRevocationStore.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("JWT가 없거나 유효하지 않거나 만료되면 관리자 API는 실제 HTTP 401을 반환한다")
    void rejectMissingInvalidAndExpiredTokens() throws Exception {
        assertUnauthorized(null);
        assertUnauthorized("not-a-jwt");

        JwtProvider expiredTokenProvider = new JwtProvider(SECRET_KEY, -1L, 60_000L);
        assertUnauthorized(expiredTokenProvider.createAccessToken("1", "ROOT"));
    }

    @Test
    @DisplayName("Refresh Token과 폐기된 Access Token은 관리자 API에 사용할 수 없다")
    void rejectWrongTypeAndRevokedTokens() throws Exception {
        userMapper.save(user(1L, "ROOT"));
        assertUnauthorized(jwtProvider.createRefreshToken("1"));

        String revoked = jwtProvider.createAccessToken("1", "ROOT");
        tokenRevocationStore.revoke(revoked, jwtProvider.getExpiration(revoked));
        assertUnauthorized(revoked);
    }

    @Test
    @DisplayName("DB의 USER 역할은 토큰이나 요청값의 역할을 조작해도 실제 HTTP 403을 반환한다")
    void rejectUserEvenWhenClientClaimsAdminRole() throws Exception {
        userMapper.save(user(1L, "USER"));
        String tokenWithRootClaim = jwtProvider.createAccessToken("1", "ROOT");

        var result = mockMvc.perform(get("/api/admin/me")
                        .param("userId", "2")
                        .param("role", "ROOT")
                        .header("Authorization", "Bearer " + tokenWithRootClaim))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode body = responseBody(result.getResponse().getContentAsString());
        assertEquals(403, body.path("statusCode").asInt());
        assertEquals("/api/admin/me", body.path("path").asText());
        assertFalse(body.path("error").asText().isBlank());
    }

    @Test
    @DisplayName("DB에 존재하지 않는 토큰 사용자는 관리자 API에서 HTTP 401을 반환한다")
    void rejectDeletedUser() throws Exception {
        assertUnauthorized(jwtProvider.createAccessToken("99", "ROOT"));
    }

    @Test
    @DisplayName("ROOT, MIDDLE, DEFAULT 역할은 DB의 현재 역할로 관리자 API에 접근한다")
    void allowAllAdminRolesFromDatabase() throws Exception {
        String[] roles = {"ROOT", "MIDDLE", "DEFAULT"};

        for (int index = 0; index < roles.length; index++) {
            long userId = index + 1L;
            String role = roles[index];
            userMapper.save(user(userId, role));
            String tokenWithUserClaim = jwtProvider.createAccessToken(String.valueOf(userId), "USER");

            var result = mockMvc.perform(get("/api/admin/me")
                            .header("Authorization", "Bearer " + tokenWithUserClaim))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = responseBody(result.getResponse().getContentAsString());
            JsonNode data = body.path("data");
            assertEquals(200, body.path("statusCode").asInt());
            assertEquals(userId, data.path("userId").asLong());
            assertEquals(role, data.path("role").asText());
            assertFalse(data.has("password"));
            assertFalse(data.has("token"));
            assertFalse(data.has("phone"));
            assertFalse(data.has("birthDate"));
        }
    }

    @Test
    @DisplayName("관리자 보안 추가 이후에도 일반 API는 기존 permitAll 정책을 유지한다")
    void keepExistingPublicApiPolicy() throws Exception {
        var result = mockMvc.perform(get("/api/public/ping"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("pong", result.getResponse().getContentAsString());
    }

    private void assertUnauthorized(String token) throws Exception {
        var request = get("/api/admin/me");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        var result = mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode body = responseBody(result.getResponse().getContentAsString());
        assertEquals(401, body.path("statusCode").asInt());
        assertEquals("/api/admin/me", body.path("path").asText());
        assertFalse(body.path("error").asText().isBlank());
    }

    private JsonNode responseBody(String content) throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(content);
        assertTrue(body.isObject());
        return body;
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
    static class TestWebConfig {

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
        AdminAuthMapper adminAuthMapper() {
            return new EmptyAdminAuthMapper();
        }

        @Bean
        AdminAuthorizationService adminAuthorizationService(
                InMemoryUserMapper userMapper,
                AdminAuthMapper adminAuthMapper
        ) {
            return new AdminAuthorizationService(userMapper, adminAuthMapper);
        }

        @Bean
        AdminAuthController adminAuthController(AdminAuthorizationService service) {
            return new AdminAuthController(service);
        }

        @Bean
        PublicTestController publicTestController() {
            return new PublicTestController();
        }
    }

    @RestController
    static class PublicTestController {

        @GetMapping("/api/public/ping")
        String ping() {
            return "pong";
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

    static class EmptyAdminAuthMapper implements AdminAuthMapper {

        @Override
        public List<UserVO> getAdmins(Set<String> roles, long offset, int size) {
            return List.of();
        }

        @Override
        public long getAdminCounts(Set<String> roles) {
            return 0;
        }
    }
}
