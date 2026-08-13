package com.example.project.user.crypto;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
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
public class UserPiiCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int ENVELOPE_PARTS = 3;

    private final UserPiiKeyProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText, UserPiiField field) {
        if (plainText == null) {
            return null;
        }

        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        String keyId = keyProvider.getActiveKeyId();

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keyProvider.getActiveEncryptionKey(),
                    new GCMParameterSpec(TAG_BITS, iv)
            );
            cipher.updateAAD(field.associatedData().getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return keyId + ":"
                    + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw cryptoError();
        }
    }

    public String decrypt(String envelope, UserPiiField field) {
        if (envelope == null) {
            return null;
        }

        try {
            String[] parts = envelope.split(":", ENVELOPE_PARTS);
            if (parts.length != ENVELOPE_PARTS) {
                throw cryptoError();
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            if (iv.length != IV_BYTES) {
                throw cryptoError();
            }
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keyProvider.getEncryptionKey(parts[0]),
                    new GCMParameterSpec(TAG_BITS, iv)
            );
            cipher.updateAAD(field.associatedData().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw cryptoError();
        }
    }

    private ServiceException cryptoError() {
        return new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
    }
}
