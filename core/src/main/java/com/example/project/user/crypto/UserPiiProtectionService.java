package com.example.project.user.crypto;

import com.example.project.recipient.domain.RecipientVO;
import com.example.project.user.domain.UserVO;
import lombok.RequiredArgsConstructor;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserPiiProtectionService {

    private final UserPiiCryptoService cryptoService;
    private final UserPiiHmacService hmacService;

    public void protect(UserVO user) {
        String email = hmacService.normalizeEmail(user.getEmail());
        String phone = user.getPhone().trim();
        String name = user.getUserName().trim();

        user.setEmail(email);
        user.setPhone(phone);
        user.setUserName(name);
        user.setEmailEncrypted(cryptoService.encrypt(email, UserPiiField.EMAIL));
        user.setEmailHmac(hmacService.email(email));
        user.setPhoneEncrypted(cryptoService.encrypt(phone, UserPiiField.PHONE));
        user.setPhoneHmac(hmacService.phone(phone));
        user.setUserNameEncrypted(cryptoService.encrypt(name, UserPiiField.USER_NAME));
    }

    public UserVO reveal(UserVO user) {
        if (user == null) {
            return null;
        }
        if (user.getEmailEncrypted() != null) {
            user.setEmail(decryptEmail(user.getEmailEncrypted()));
        }
        if (user.getPhoneEncrypted() != null) {
            user.setPhone(decryptPhone(user.getPhoneEncrypted()));
        }
        if (user.getUserNameEncrypted() != null) {
            user.setUserName(decryptUserName(user.getUserNameEncrypted()));
        }
        return user;
    }

    public void protect(RecipientVO recipient) {
        String familyName = recipient.getFamilyName().trim();
        String birthDate = recipient.getBirthDate().toString();
        recipient.setFamilyName(familyName);
        recipient.setFamilyNameEncrypted(
                cryptoService.encrypt(familyName, UserPiiField.FAMILY_NAME)
        );
        recipient.setBirthDateEncrypted(
                cryptoService.encrypt(birthDate, UserPiiField.FAMILY_BIRTH_DATE)
        );
    }

    public RecipientVO reveal(RecipientVO recipient) {
        if (recipient == null) {
            return null;
        }
        if (recipient.getFamilyNameEncrypted() != null) {
            recipient.setFamilyName(decryptFamilyName(recipient.getFamilyNameEncrypted()));
        }
        if (recipient.getBirthDateEncrypted() != null) {
            recipient.setBirthDate(decryptFamilyBirthDate(recipient.getBirthDateEncrypted()));
        }
        return recipient;
    }

    public String emailLookup(String email) {
        return hmacService.email(email);
    }

    public String phoneLookup(String phone) {
        return hmacService.phone(phone);
    }

    public String decryptEmail(String encrypted) {
        return cryptoService.decrypt(encrypted, UserPiiField.EMAIL);
    }

    public String decryptPhone(String encrypted) {
        return cryptoService.decrypt(encrypted, UserPiiField.PHONE);
    }

    public String decryptUserName(String encrypted) {
        return cryptoService.decrypt(encrypted, UserPiiField.USER_NAME);
    }

    public String decryptFamilyName(String encrypted) {
        return cryptoService.decrypt(encrypted, UserPiiField.FAMILY_NAME);
    }

    public LocalDate decryptFamilyBirthDate(String encrypted) {
        try {
            return LocalDate.parse(
                    cryptoService.decrypt(encrypted, UserPiiField.FAMILY_BIRTH_DATE)
            );
        } catch (RuntimeException exception) {
            throw new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}
