package com.example.project.user.crypto;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserPiiHmacService {

    private static final String ALGORITHM = "HmacSHA256";

    private final UserPiiKeyProvider keyProvider;

    public String email(String email) {
        return digest(normalizeEmail(email));
    }

    public String phone(String phone) {
        return digest(normalizePhone(phone));
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizePhone(String phone) {
        return phone.trim().replaceAll("[^0-9]", "");
    }

    private String digest(String normalizedValue) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keyProvider.getHmacKey());
            byte[] digest = mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8));
            return keyProvider.getHmacKeyId() + ":" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}
