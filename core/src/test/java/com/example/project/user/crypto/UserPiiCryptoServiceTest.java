package com.example.project.user.crypto;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.support.PiiTestSupport;
import com.example.project.user.domain.UserVO;
import com.example.project.recipient.domain.RecipientVO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPiiCryptoServiceTest {

    private final UserPiiProtectionService protectionService =
            PiiTestSupport.protectionService();

    @Test
    void protectsAndRevealsUserFieldsWithRandomIvAndStableLookupHmac() {
        UserVO first = user(" User@Example.com ", "010-1234-5678", " 홍길동 ");
        UserVO second = user("user@example.com", "01012345678", "홍길동");

        protectionService.protect(first);
        protectionService.protect(second);

        assertTrue(first.getEmailEncrypted().startsWith("v1:"));
        assertNotEquals(first.getEmailEncrypted(), second.getEmailEncrypted());
        assertEquals(first.getEmailHmac(), second.getEmailHmac());
        assertEquals(first.getPhoneHmac(), second.getPhoneHmac());

        protectionService.reveal(first);
        assertEquals("user@example.com", first.getEmail());
        assertEquals("010-1234-5678", first.getPhone());
        assertEquals("홍길동", first.getUserName());
    }

    @Test
    void rejectsCiphertextMovedToAnotherFieldOrTampered() {
        UserVO user = user("user@example.com", "010-1234-5678", "홍길동");
        protectionService.protect(user);

        String original = user.getEmailEncrypted();
        user.setEmailEncrypted(original.substring(0, original.length() - 1) + "A");
        ServiceException tampered = assertThrows(
                ServiceException.class,
                () -> protectionService.reveal(user)
        );
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, tampered.getResponseCode());

        assertThrows(
                ServiceException.class,
                () -> protectionService.decryptPhone(original)
        );
    }

    @Test
    void protectsFamilyNameAndIsoBirthDateAndRejectsWrongAad() {
        RecipientVO first = family("Beneficiary", LocalDate.of(2008, 2, 29));
        RecipientVO second = family("Beneficiary", LocalDate.of(2008, 2, 29));

        protectionService.protect(first);
        protectionService.protect(second);

        assertNotEquals(first.getFamilyNameEncrypted(), second.getFamilyNameEncrypted());
        assertNotEquals(first.getBirthDateEncrypted(), second.getBirthDateEncrypted());
        assertEquals(
                LocalDate.of(2008, 2, 29),
                protectionService.decryptFamilyBirthDate(first.getBirthDateEncrypted())
        );
        assertThrows(
                ServiceException.class,
                () -> protectionService.decryptUserName(first.getFamilyNameEncrypted())
        );

        protectionService.reveal(first);
        assertEquals("Beneficiary", first.getFamilyName());
        assertEquals(LocalDate.of(2008, 2, 29), first.getBirthDate());
    }

    @Test
    void rejectsMissingInvalidOrReusedKeysAndSupportsKeyVersionReads() {
        String aesV1 = testKey('a');
        String aesV2 = testKey('c');
        String hmac = testKey('b');

        assertThrows(ServiceException.class, () -> new UserPiiKeyProvider("v1", "", hmac));
        assertThrows(ServiceException.class, () -> new UserPiiKeyProvider("v1", "bad", hmac));
        assertThrows(ServiceException.class, () -> new UserPiiKeyProvider("v1", aesV1, aesV1));

        UserPiiCryptoService v1Crypto = new UserPiiCryptoService(
                new UserPiiKeyProvider("v1", aesV1, aesV2, hmac)
        );
        String v1Envelope = v1Crypto.encrypt("value", UserPiiField.EMAIL);
        UserPiiCryptoService v2Crypto = new UserPiiCryptoService(
                new UserPiiKeyProvider("v2", aesV1, aesV2, hmac)
        );

        assertTrue(v1Envelope.startsWith("v1:"));
        assertEquals("value", v2Crypto.decrypt(v1Envelope, UserPiiField.EMAIL));
        assertTrue(v2Crypto.encrypt("value", UserPiiField.EMAIL).startsWith("v2:"));
    }

    private RecipientVO family(String name, LocalDate birthDate) {
        RecipientVO family = new RecipientVO();
        family.setFamilyName(name);
        family.setBirthDate(birthDate);
        return family;
    }

    private String testKey(char value) {
        return Base64.getEncoder().encodeToString(
                String.valueOf(value).repeat(32).getBytes(StandardCharsets.UTF_8)
        );
    }

    private UserVO user(String email, String phone, String name) {
        UserVO user = new UserVO();
        user.setEmail(email);
        user.setPhone(phone);
        user.setUserName(name);
        return user;
    }
}
