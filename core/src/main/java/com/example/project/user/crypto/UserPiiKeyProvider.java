package com.example.project.user.crypto;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@PropertySource("classpath:application.properties")
public class UserPiiKeyProvider {

    private static final int KEY_BYTES = 32;
    private static final String HMAC_KEY_ID = "v1";

    private final String activeKeyId;
    private final Map<String, SecretKey> encryptionKeys = new HashMap<>();
    private final SecretKey hmacKey;

    @Autowired
    public UserPiiKeyProvider(
            @Value("${ai.conversation.crypto.active-key-id}") String activeKeyId,
            @Value("${ai.conversation.crypto.key-v1}") String encryptionKeyV1,
            @Value("${ai.conversation.crypto.key-v2:}") String encryptionKeyV2,
            @Value("${pii.search.hmac-key-v1:}") String hmacKeyV1
    ) {
        this.activeKeyId = requireText(activeKeyId);
        addEncryptionKey("v1", encryptionKeyV1);
        addEncryptionKey("v2", encryptionKeyV2);
        this.hmacKey = decodeKey(hmacKeyV1, "HmacSHA256");

        SecretKey activeKey = encryptionKeys.get(this.activeKeyId);
        if (activeKey == null || keyEquals(activeKey, hmacKey)) {
            throw configurationError();
        }
        for (SecretKey encryptionKey : encryptionKeys.values()) {
            if (keyEquals(encryptionKey, hmacKey)) {
                throw configurationError();
            }
        }
    }

    public UserPiiKeyProvider(
            String activeKeyId,
            String encryptionKeyV1,
            String hmacKeyV1
    ) {
        this(activeKeyId, encryptionKeyV1, "", hmacKeyV1);
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public SecretKey getActiveEncryptionKey() {
        return getEncryptionKey(activeKeyId);
    }

    public SecretKey getEncryptionKey(String keyId) {
        SecretKey key = encryptionKeys.get(keyId);
        if (key == null) {
            throw configurationError();
        }
        return key;
    }

    public String getHmacKeyId() {
        return HMAC_KEY_ID;
    }

    public SecretKey getHmacKey() {
        return hmacKey;
    }

    private void addEncryptionKey(String keyId, String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return;
        }
        encryptionKeys.put(keyId, decodeKey(encodedKey, "AES"));
    }

    private SecretKey decodeKey(String encodedKey, String algorithm) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw configurationError();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
            if (decoded.length != KEY_BYTES) {
                throw configurationError();
            }
            return new SecretKeySpec(decoded, algorithm);
        } catch (IllegalArgumentException exception) {
            throw configurationError();
        }
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw configurationError();
        }
        return value.trim();
    }

    private boolean keyEquals(SecretKey first, SecretKey second) {
        return MessageDigest.isEqual(first.getEncoded(), second.getEncoded());
    }

    private ServiceException configurationError() {
        return new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
    }
}
