package com.example.project.consultation.crypto.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@PropertySource("classpath:application.properties")
public class ConversationKeyProvider {
    private static final int AES_256_KEY_BYTES = 32;

    private final String activeKeyId;
    private final Map<String, SecretKey> keys;

    public ConversationKeyProvider(
            @Value("${ai.conversation.crypto.active-key-id}")
            String activeKeyId,
            @Value("${ai.conversation.crypto.key-v1}")
            String keyV1,
            @Value("${ai.conversation.crypto.key-v2}")
            String keyV2
    ) {
        this.activeKeyId = activeKeyId;
        this.keys = new HashMap<>();

        addKey("v1", keyV1);
        addKey("v2", keyV2);

        if(!keys.containsKey(activeKeyId)) {
            throw new IllegalStateException(
                    "AI 대화 활성 암호화 키를 찾을 수 없습니다."
            );
        }
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public SecretKey getActiveKey(){
        return getKey(activeKeyId);
    }

    public SecretKey getKey(String keyId) {
        SecretKey key = keys.get(keyId);

        if(key == null) {
            throw new IllegalStateException(
                    "AI 대화 복호화 키를 찾을 수 없습니다."
            );
        }
        return key;
    }

    private void addKey(String keyId, String base64Key) {
        if (base64Key == null || base64Key.isBlank()){
            return;
        }

        byte[] decoded = Base64.getDecoder().decode(
                base64Key.trim()
        );

        if (decoded.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException(
                    "AI 대화 암호화 키는 32바이트여야 합니다."
            );
        }

        keys.put(
                keyId,
                new SecretKeySpec(decoded, "AES")
        );
    }
}
