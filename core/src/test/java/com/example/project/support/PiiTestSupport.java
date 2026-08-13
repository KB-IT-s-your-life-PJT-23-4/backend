package com.example.project.support;

import com.example.project.user.crypto.UserPiiCryptoService;
import com.example.project.user.crypto.UserPiiHmacService;
import com.example.project.user.crypto.UserPiiKeyProvider;
import com.example.project.user.crypto.UserPiiProtectionService;
import com.example.project.user.domain.UserVO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PiiTestSupport {

    private PiiTestSupport() {
    }

    public static UserPiiProtectionService protectionService() {
        UserPiiKeyProvider keyProvider = new UserPiiKeyProvider(
                "v1",
                key('a'),
                key('b')
        );
        return new UserPiiProtectionService(
                new UserPiiCryptoService(keyProvider),
                new UserPiiHmacService(keyProvider)
        );
    }

    public static String emailLookup(String email) {
        UserPiiKeyProvider keyProvider = keyProvider();
        return new UserPiiHmacService(keyProvider).email(email);
    }

    public static boolean emailMatches(UserVO user, String lookup) {
        if (user == null || user.getEmail() == null) {
            return false;
        }
        String expected = user.getEmailHmac() == null
                ? emailLookup(user.getEmail())
                : user.getEmailHmac();
        return expected.equals(lookup);
    }

    private static UserPiiKeyProvider keyProvider() {
        return new UserPiiKeyProvider("v1", key('a'), key('b'));
    }

    private static String key(char value) {
        return Base64.getEncoder().encodeToString(
                String.valueOf(value).repeat(32).getBytes(StandardCharsets.UTF_8)
        );
    }
}
