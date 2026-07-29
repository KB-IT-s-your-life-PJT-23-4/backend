package com.example.project.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtProviderTest {

    private static final String SECRET_KEY =
            "test-secret-key-for-jwt-provider-must-be-at-least-32-bytes";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET_KEY, 60_000L, 120_000L);
    }

    @Test
    @DisplayName("Access Token에 사용자와 권한 정보를 포함한다")
    void createAccessToken() {
        String token = jwtProvider.createAccessToken("1", "USER");
        Claims claims = jwtProvider.parse(token);

        assertEquals("1", claims.getSubject());
        assertEquals("USER", claims.get("role"));
        assertEquals("ACCESS", claims.get("tokenType"));
        assertTrue(jwtProvider.isValidAccessToken(token));
        assertFalse(jwtProvider.isValidRefreshToken(token));
    }

    @Test
    @DisplayName("Refresh Token은 Access Token과 구분하고 매번 새로 생성한다")
    void createRefreshToken() {
        String firstToken = jwtProvider.createRefreshToken("1");
        String secondToken = jwtProvider.createRefreshToken("1");

        assertNotEquals(firstToken, secondToken);
        assertEquals("REFRESH", jwtProvider.parse(firstToken).get("tokenType"));
        assertTrue(jwtProvider.isValidRefreshToken(firstToken));
        assertFalse(jwtProvider.isValidAccessToken(firstToken));
    }

    @Test
    @DisplayName("만료된 토큰을 식별한다")
    void expiredToken() {
        JwtProvider expiredTokenProvider = new JwtProvider(SECRET_KEY, -60_000L, -60_000L);
        String token = expiredTokenProvider.createAccessToken("1", "USER");

        assertTrue(expiredTokenProvider.isExpired(token));
        assertFalse(expiredTokenProvider.isValid(token));
    }
}
