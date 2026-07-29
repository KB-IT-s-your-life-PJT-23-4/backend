package com.example.project.security;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    @Test
    @DisplayName("Bearer Authorization 헤더에서 Access Token을 추출한다")
    void resolveAccessToken() {
        String accessToken = JwtUtil.resolveAccessToken("Bearer access-token");

        assertEquals("access-token", accessToken);
    }

    @Test
    @DisplayName("Authorization 헤더가 없거나 Bearer 형식이 아니면 토큰을 반환하지 않는다")
    void resolveInvalidAccessToken() {
        assertNull(JwtUtil.resolveAccessToken(null));
        assertNull(JwtUtil.resolveAccessToken("Basic credentials"));
        assertNull(JwtUtil.resolveAccessToken("Bearer "));
    }

    @Test
    @DisplayName("필수 Access Token이 없으면 인증 예외를 발생시킨다")
    void resolveRequiredAccessToken() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> JwtUtil.resolveRequiredAccessToken(null)
        );

        assertEquals(ResponseCode.UNAUTHORIZED, exception.getResponseCode());
    }

    @Test
    @DisplayName("JWT Claims에서 사용자 ID와 권한을 추출한다")
    void getUserInformation() {
        Claims claims = Jwts.claims();
        claims.setSubject("1");
        claims.put("role", "USER");

        assertEquals("1", JwtUtil.getUserId(claims));
        assertEquals("USER", JwtUtil.getRole(claims));
    }
}
