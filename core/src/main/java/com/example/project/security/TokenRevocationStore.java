package com.example.project.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TokenRevocationStore {

    private final ConcurrentMap<String, Long> revokedTokens = new ConcurrentHashMap<>();

    public void revoke(String token, Date expiration) {
        removeExpiredTokens();
        revokedTokens.put(hash(token), expiration.getTime());
    }

    public boolean isRevoked(String token) {
        String tokenHash = hash(token);
        Long expirationTime = revokedTokens.get(tokenHash);

        if (expirationTime == null) {
            return false;
        }

        if (expirationTime <= Instant.now().toEpochMilli()) {
            revokedTokens.remove(tokenHash, expirationTime);
            return false;
        }

        return true;
    }

    private void removeExpiredTokens() {
        long now = Instant.now().toEpochMilli();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
