package com.example.project.security;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import io.jsonwebtoken.Claims;

public final class JwtUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    private JwtUtil() {
    }

    public static String resolveAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    public static String resolveRequiredAccessToken(String authorizationHeader) {
        String accessToken = resolveAccessToken(authorizationHeader);

        if (accessToken == null) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        return accessToken;
    }

    public static String getUserId(Claims claims) {
        return claims.getSubject();
    }

    public static String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
