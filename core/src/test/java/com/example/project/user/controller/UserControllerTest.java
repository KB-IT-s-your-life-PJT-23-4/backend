package com.example.project.user.controller;

import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UserControllerTest {

    private static final Long USER_ID = 1L;

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;
    private FakeUserMapper userMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        userMapper = new FakeUserMapper();
        userMapper.add(user(
                USER_ID,
                "user@example.com",
                passwordEncoder.encode("password123!"),
                "홍길동"
        ));

        UserService userService = new UserService(userMapper, passwordEncoder);
        UserController controller = new UserController(userService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/signup은 회원을 등록하고 201 공통 응답을 반환한다")
    void signupApi() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "New@Example.com",
                                  "password": "password123!",
                                  "name": "김철수"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(201, body.get("statusCode").asInt());
        assertEquals("/api/auth/signup", body.get("path").asText());
        assertEquals("new@example.com", body.at("/data/email").asText());
        assertEquals("김철수", body.at("/data/name").asText());
        assertTrue(body.has("timestamp"));
        assertTrue(body.has("message"));
        assertFalse(body.has("error"));
    }

    @Test
    @DisplayName("회원가입 Validation 실패는 400 공통 예외 응답을 반환한다")
    void rejectInvalidSignupApi() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "short",
                                  "name": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(407, body.get("statusCode").asInt());
        assertEquals("/api/auth/signup", body.get("path").asText());
        assertTrue(body.has("error"));
        assertFalse(body.has("data"));
    }

    @Test
    @DisplayName("중복 이메일 회원가입은 409를 반환한다")
    void rejectDuplicateEmailSignupApi() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "USER@example.com",
                                  "password": "password123!",
                                  "name": "김철수"
                                }
                                """))
                .andExpect(status().isConflict())
                .andReturn();

        assertEquals(406, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("GET /api/auth/check-email은 이메일 사용 가능 여부를 반환한다")
    void checkEmailApi() throws Exception {
        MvcResult availableResult = mockMvc.perform(get("/api/auth/check-email")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult duplicateResult = mockMvc.perform(get("/api/auth/check-email")
                        .param("email", "USER@example.com"))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(body(availableResult).at("/data/available").asBoolean());
        assertFalse(body(duplicateResult).at("/data/available").asBoolean());
    }

    @Test
    @DisplayName("GET /api/users/me는 인증 사용자의 프로필을 반환한다")
    void getMyProfileApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .principal(authentication(USER_ID)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(200, body.get("statusCode").asInt());
        assertEquals(USER_ID, body.at("/data/userId").asLong());
        assertEquals("user@example.com", body.at("/data/email").asText());
    }

    @Test
    @DisplayName("PUT /api/users/me는 인증 사용자의 프로필을 수정한다")
    void updateMyProfileApi() throws Exception {
        MvcResult result = mockMvc.perform(put("/api/users/me")
                        .principal(authentication(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "updated@example.com",
                                  "name": "수정이름"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(203, body.get("statusCode").asInt());
        assertEquals("updated@example.com", body.at("/data/email").asText());
        assertEquals("수정이름", body.at("/data/name").asText());
    }

    @Test
    @DisplayName("회원 프로필 수정 Validation 실패는 400을 반환한다")
    void rejectInvalidProfileUpdateApi() throws Exception {
        MvcResult result = mockMvc.perform(put("/api/users/me")
                        .principal(authentication(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "name": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(407, body(result).get("statusCode").asInt());
        assertEquals("user@example.com", userMapper.findById(USER_ID).getEmail());
    }

    @Test
    @DisplayName("DELETE /api/users/me는 인증 사용자를 삭제한다")
    void deleteMyAccountApi() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/users/me")
                        .principal(authentication(USER_ID)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(204, body(result).get("statusCode").asInt());
        assertFalse(userMapper.users.containsKey(USER_ID));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 프로필 요청은 404를 반환한다")
    void missingUserApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .principal(authentication(999L)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(410, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("인증 정보가 없는 회원 관리 요청은 401을 반환한다")
    void rejectUnauthenticatedUserApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals(401, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("다른 사용자로 인증하면 기존 사용자의 정보를 조회할 수 없다")
    void rejectOtherUserAccess() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .principal(authentication(2L)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(410, body(result).get("statusCode").asInt());
        assertTrue(userMapper.users.containsKey(USER_ID));
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private UserVO user(Long userId, String email, String password, String name) {
        return new UserVO(
                userId,
                email,
                password,
                name,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );
    }

    private static class FakeUserMapper implements UserMapper {

        private final Map<Long, UserVO> users = new HashMap<>();
        private long sequence = 100L;

        void add(UserVO user) {
            users.put(user.getUserId(), user);
        }

        @Override
        public UserVO findById(Long userId) {
            return users.get(userId);
        }

        @Override
        public UserVO findByEmail(String email) {
            return users.values().stream()
                    .filter(user -> user.getEmail().equals(email))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int insert(UserVO user) {
            user.setUserId(++sequence);
            user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            user.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            users.put(user.getUserId(), user);
            return 1;
        }

        @Override
        public int update(UserVO user) {
            if (!users.containsKey(user.getUserId())) {
                return 0;
            }
            user.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
            users.put(user.getUserId(), user);
            return 1;
        }

        @Override
        public int deleteById(Long userId) {
            return users.remove(userId) == null ? 0 : 1;
        }
    }
}
