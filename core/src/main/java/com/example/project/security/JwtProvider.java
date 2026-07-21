package com.example.project.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String key,
            @Value("${jwt.expiration}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {

        this.secretKey = Keys.hmacShaKeyFor(key.getBytes());
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String createAccessToken(String subject, String role) {
        return buildToken(subject, role, accessExpirationMs);
    }

    public String createRefreshToken(String subject) {
        return buildToken(subject, null, refreshExpirationMs);
    }

    private String buildToken(String subject, String role, long expirationMs) {
        Date now = new Date();

        var builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs));

        if (role != null) {
            builder.claim("role", role);
        }

        return builder.signWith(secretKey, SignatureAlgorithm.HS256).compact();
    }

    // 클라이언트는 string으로 보내기에 이를 header, payload, signature로 분리해야하므로 파싱해야함
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 토큰 검증
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parse(token).getSubject();
    }
}
