package com.example.project.auth.controller;

import com.example.project.auth.service.AuthService;
import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.security.JwtProvider;
import com.example.project.security.TokenRevocationStore;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuthControllerTest {

    private static final String SECRET_KEY =
            "test-secret-key-for-auth-controller-must-be-at-least-32-bytes";

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;
    private JwtProvider jwtProvider;
    private TokenRevocationStore tokenRevocationStore;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        jwtProvider = new JwtProvider(SECRET_KEY, 60_000L, 120_000L);
        tokenRevocationStore = new TokenRevocationStore();

        UserVO user = new UserVO(
                1L,
                "user@example.com",
                passwordEncoder.encode("password123!"),
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "010-1234-5678",
                "USER",
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0),
                null
        );
        AuthService authService = new AuthService(
                new FakeUserMapper(user),
                passwordEncoder,
                jwtProvider,
                tokenRevocationStore
        );

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/login은 토큰과 비밀번호가 제외된 회원 정보를 반환한다")
    void loginApi() throws Exception {
        MvcResult result = login("User@Example.com", "password123!")
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = body(result);
        JsonNode data = response.get("data");
        assertEquals(205, response.get("statusCode").asInt());
        assertFalse(data.get("accessToken").asText().isBlank());
        assertFalse(data.get("refreshToken").asText().isBlank());
        assertEquals(1L, data.at("/user/userId").asLong());
        assertFalse(data.get("user").has("password"));
        assertTrue(jwtProvider.isValidAccessToken(data.get("accessToken").asText()));
        assertTrue(jwtProvider.isValidRefreshToken(data.get("refreshToken").asText()));
    }

    @Test
    @DisplayName("로그인 자격 증명이 틀리면 401 공통 오류 응답을 반환한다")
    void rejectInvalidLoginApi() throws Exception {
        MvcResult result = login("user@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode response = body(result);
        assertEquals(401, response.get("statusCode").asInt());
        assertFalse(response.get("error").asText().isBlank());
        assertFalse(response.has("data"));
    }

    @Test
    @DisplayName("로그인 입력값 검증에 실패하면 400 공통 오류 응답을 반환한다")
    void rejectInvalidLoginRequestApi() throws Exception {
        MvcResult result = login("invalid-email", "")
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode response = body(result);
        assertEquals(407, response.get("statusCode").asInt());
        assertEquals("/api/auth/login", response.get("path").asText());
    }

    @Test
    @DisplayName("POST /api/auth/refresh는 토큰을 회전하고 기존 Refresh Token을 폐기한다")
    void refreshApi() throws Exception {
        JsonNode loginData = body(login("user@example.com", "password123!").andReturn()).get("data");
        String oldAccessToken = loginData.get("accessToken").asText();
        String oldRefreshToken = loginData.get("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenBody(oldRefreshToken))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = body(result);
        JsonNode refreshed = response.get("data");
        assertEquals(200, response.get("statusCode").asInt());
        assertNotEquals(oldAccessToken, refreshed.get("accessToken").asText());
        assertNotEquals(oldRefreshToken, refreshed.get("refreshToken").asText());
        assertTrue(tokenRevocationStore.isRevoked(oldRefreshToken));

        MvcResult reusedResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenBody(oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertEquals(401, body(reusedResult).get("statusCode").asInt());
    }

    @Test
    @DisplayName("POST /api/auth/logout은 Access Token과 Refresh Token을 모두 폐기한다")
    void logoutApi() throws Exception {
        JsonNode loginData = body(login("user@example.com", "password123!").andReturn()).get("data");
        String accessToken = loginData.get("accessToken").asText();
        String refreshToken = loginData.get("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenBody(refreshToken))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = body(result);
        assertEquals(206, response.get("statusCode").asInt());
        assertFalse(response.has("data"));
        assertTrue(tokenRevocationStore.isRevoked(accessToken));
        assertTrue(tokenRevocationStore.isRevoked(refreshToken));
    }

    @Test
    @DisplayName("로그아웃 요청에 Authorization 헤더가 없으면 401을 반환한다")
    void rejectLogoutWithoutAuthorizationHeader() throws Exception {
        JsonNode loginData = body(login("user@example.com", "password123!").andReturn()).get("data");
        String refreshToken = loginData.get("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenBody(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals(401, body(result).get("statusCode").asInt());
        assertFalse(tokenRevocationStore.isRevoked(refreshToken));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginBody(email, password))));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private record LoginBody(String email, String password) {
    }

    private record TokenBody(String refreshToken) {
    }

    private static class FakeUserMapper implements UserMapper {

        private final UserVO user;

        private FakeUserMapper(UserVO user) {
            this.user = user;
        }

        @Override
        public UserVO findById(Long userId) {
            return user.getUserId().equals(userId) ? user : null;
        }

        @Override
        public UserVO findByEmail(String email) {
            return user.getEmail().equals(email) ? user : null;
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
