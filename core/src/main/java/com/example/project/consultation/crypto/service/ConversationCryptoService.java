package com.example.project.consultation.crypto.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.crypto.domain.EncryptedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ConversationCryptoService {

    private static final String ALGORITHM = "AES-256-GCM";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int IV_LENGTH_BYTES = 12;
    private static final int AUTH_TAG_LENGTH_BITS = 128;

    private final ConversationKeyProvider keyProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedPayload encrypt(String plainText, String associatedData) {
        if (plainText == null){
            return null;
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        String keyId = keyProvider.getActiveKeyId();

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keyProvider.getActiveKey(),
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv)
            );

            /*
             * AAD는 암호문을 특정 사용자, 대화, 질문에 묶습니다.
             * 암호문이 다른 대화 행으로 복사되면 복호화가 실패합니다.
             */
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return EncryptedPayload.builder()
                    .algorithm(ALGORITHM)
                    .keyId(keyId)
                    .iv(Base64.getEncoder().encodeToString(iv))
                    .ciphertext(Base64.getEncoder().encodeToString(encrypted))
                    .build();
        } catch (GeneralSecurityException exception) {
            /*
             * 평문이나 키를 로그에 출력하면 안 됩니다.
             */
            throw new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt(EncryptedPayload payload, String associatedData){
        if (payload == null) {
            return null;
        }

        if(!ALGORITHM.equals(payload.getAlgorithm())) {
            throw new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

        try {
            byte[] iv = Base64.getDecoder().decode(payload.getIv());

            byte[] ciphertext = Base64.getDecoder().decode(payload.getCiphertext());

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keyProvider.getKey(payload.getKeyId()),
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv)
            );

            cipher.updateAAD(
                    associatedData.getBytes(StandardCharsets.UTF_8)
            );

            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            /*
             * 복호화 실패 원인을 외부에 자세히 노출하면 안 됩니다.
             */
            throw new ServiceException(
                    ResponseCode.INTERNAL_SERVER_ERROR
            );
        }
    }
}
