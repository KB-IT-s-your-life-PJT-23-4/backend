package com.example.project.auth.service;

import com.example.project.auth.dto.request.LoginRequest;
import com.example.project.auth.dto.request.LogoutRequest;
import com.example.project.auth.dto.request.TokenRefreshRequest;
import com.example.project.auth.dto.response.AuthTokenResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.security.JwtProvider;
import com.example.project.security.TokenRevocationStore;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final String SECRET_KEY =
            "test-secret-key-for-auth-service-must-be-at-least-32-bytes";

    private FakeUserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtProvider jwtProvider;
    private TokenRevocationStore tokenRevocationStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userMapper = new FakeUserMapper(createUser());
        jwtProvider = new JwtProvider(SECRET_KEY, 60_000L, 120_000L);
        tokenRevocationStore = new TokenRevocationStore();
        authService = new AuthService(
                userMapper,
                passwordEncoder,
                jwtProvider,
                tokenRevocationStore,
                com.example.project.support.PiiTestSupport.protectionService()
        );
    }

    @Test
    @DisplayName("이메일과 비밀번호가 일치하면 Access Token과 Refresh Token을 발급한다")
    void login() {
        AuthTokenResponse response = authService.login(
                new LoginRequest(" User@Example.com ", "password123!")
        );

        assertEquals(1L, response.user().userId());
        assertTrue(jwtProvider.isValidAccessToken(response.accessToken()));
        assertTrue(jwtProvider.isValidRefreshToken(response.refreshToken()));
        assertEquals("USER", jwtProvider.parse(response.accessToken()).get("role", String.class));
    }

    @Test
    @DisplayName("로그인 Access Token에는 DB의 실제 사용자 권한이 포함된다")
    void issueDatabaseRoleOnLogin() {
        for (String role : java.util.List.of("ROOT", "MIDDLE", "DEFAULT", "USER")) {
            userMapper.user.setRole(role);

            AuthTokenResponse response = authService.login(
                    new LoginRequest("user@example.com", "password123!")
            );

            assertEquals(role, jwtProvider.parse(response.accessToken()).get("role", String.class));
        }
    }

    @Test
    @DisplayName("이메일 또는 비밀번호가 일치하지 않으면 로그인에 실패한다")
    void loginWithInvalidCredentials() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> authService.login(
                        new LoginRequest("user@example.com", "wrong-password")
                )
        );

        assertEquals(ResponseCode.UNAUTHORIZED, exception.getResponseCode());
    }

    @Test
    @DisplayName("Refresh Token을 사용하면 토큰을 회전 발급하고 기존 토큰을 폐기한다")
    void refresh() {
        AuthTokenResponse loginResponse = authService.login(
                new LoginRequest("user@example.com", "password123!")
        );
        userMapper.user.setRole("MIDDLE");

        AuthTokenResponse refreshResponse = authService.refresh(
                new TokenRefreshRequest(loginResponse.refreshToken())
        );

        assertNotEquals(loginResponse.accessToken(), refreshResponse.accessToken());
        assertNotEquals(loginResponse.refreshToken(), refreshResponse.refreshToken());
        assertTrue(tokenRevocationStore.isRevoked(loginResponse.refreshToken()));
        assertEquals(
                "MIDDLE",
                jwtProvider.parse(refreshResponse.accessToken()).get("role", String.class)
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> authService.refresh(
                        new TokenRefreshRequest(loginResponse.refreshToken())
                )
        );
        assertEquals(ResponseCode.UNAUTHORIZED, exception.getResponseCode());
    }

    @Test
    @DisplayName("탈퇴한 회원의 기존 Refresh Token으로는 재발급할 수 없다")
    void rejectRefreshTokenAfterWithdrawal() {
        AuthTokenResponse loginResponse = authService.login(
                new LoginRequest("user@example.com", "password123!")
        );
        userMapper.deleteById(1L);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> authService.refresh(new TokenRefreshRequest(loginResponse.refreshToken()))
        );

        assertEquals(ResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("로그아웃하면 전달된 Access Token과 Refresh Token을 폐기한다")
    void logout() {
        AuthTokenResponse response = authService.login(
                new LoginRequest("user@example.com", "password123!")
        );

        authService.logout(
                new LogoutRequest(response.refreshToken()),
                response.accessToken()
        );

        assertTrue(tokenRevocationStore.isRevoked(response.accessToken()));
        assertTrue(tokenRevocationStore.isRevoked(response.refreshToken()));
        assertFalse(jwtProvider.isExpired(response.accessToken()));
    }

    @Test
    @DisplayName("로그아웃은 이미 폐기된 Refresh Token에도 성공한다")
    void logoutWithRevokedRefreshToken() {
        AuthTokenResponse response = authService.login(
                new LoginRequest("user@example.com", "password123!")
        );
        LogoutRequest request = new LogoutRequest(response.refreshToken());

        authService.logout(request, response.accessToken());
        authService.logout(request, response.accessToken());

        assertTrue(tokenRevocationStore.isRevoked(response.refreshToken()));
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 로그아웃할 때 Access Token이 없으면 실패한다")
    void logoutWithoutAccessToken() {
        AuthTokenResponse response = authService.login(
                new LoginRequest("user@example.com", "password123!")
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> authService.logout(
                        new LogoutRequest(response.refreshToken()),
                        null
                )
        );

        assertEquals(ResponseCode.UNAUTHORIZED, exception.getResponseCode());
        assertFalse(tokenRevocationStore.isRevoked(response.refreshToken()));
    }

    private UserVO createUser() {
        return new UserVO(
                1L,
                "user@example.com",
                passwordEncoder.encode("password123!"),
                "홍길동",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private static class FakeUserMapper implements UserMapper {

        private final UserVO user;
        private boolean deleted;

        private FakeUserMapper(UserVO user) {
            this.user = user;
        }

        @Override
        public UserVO findById(Long userId) {
            return !deleted && user.getUserId().equals(userId) ? user : null;
        }

        @Override
        public UserVO findByEmail(String email) {
            return !deleted
                    && com.example.project.support.PiiTestSupport.emailMatches(user, email)
                    ? user : null;
        }

        @Override
        public int insert(UserVO user) {
            return 0;
        }

        @Override
        public int update(UserVO user) {
            return 0;
        }

        @Override
        public int deleteById(Long userId) {
            if (deleted || !user.getUserId().equals(userId)) {
                return 0;
            }
            deleted = true;
            return 1;
        }
    }
}
