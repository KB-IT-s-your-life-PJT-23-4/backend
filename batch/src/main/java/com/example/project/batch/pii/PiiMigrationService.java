package com.example.project.batch.pii;

import com.example.project.recipient.domain.RecipientVO;
import com.example.project.user.crypto.UserPiiProtectionService;
import com.example.project.user.domain.UserVO;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
public class PiiMigrationService {

    private static final Logger log = LogManager.getLogger(PiiMigrationService.class);

    private final PiiMigrationMapper mapper;
    private final UserPiiProtectionService protectionService;
    private final TransactionTemplate transactionTemplate;

    public PiiMigrationReport migrate(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batch size must be between 1 and 1000");
        }

        Counters counters = new Counters();
        try {
            migrateUsers(batchSize, counters);
        } catch (RuntimeException exception) {
            counters.userFailed++;
            log.error(
                    "KAN-265 user migration stopped: scanned={}, migrated={}, verified={}, failed={}",
                    counters.userScanned, counters.userMigrated,
                    counters.userVerified, counters.userFailed
            );
            throw exception;
        }
        try {
            migrateFamilies(batchSize, counters);
        } catch (RuntimeException exception) {
            counters.familyFailed++;
            log.error(
                    "KAN-265 family migration stopped: scanned={}, migrated={}, verified={}, failed={}",
                    counters.familyScanned, counters.familyMigrated,
                    counters.familyVerified, counters.familyFailed
            );
            throw exception;
        }
        return counters.report();
    }

    private void migrateUsers(int batchSize, Counters counters) {
        long afterId = 0;
        while (true) {
            List<PiiMigrationUser> rows = mapper.selectUsersAfter(afterId, batchSize);
            if (rows.isEmpty()) {
                return;
            }
            rows.forEach(row -> transactionTemplate.executeWithoutResult(
                    status -> migrateUser(row, counters)
            ));
            afterId = rows.get(rows.size() - 1).getUserId();
        }
    }

    private void migrateFamilies(int batchSize, Counters counters) {
        long afterId = 0;
        while (true) {
            List<PiiMigrationFamily> rows = mapper.selectFamiliesAfter(afterId, batchSize);
            if (rows.isEmpty()) {
                return;
            }
            rows.forEach(row -> transactionTemplate.executeWithoutResult(
                    status -> migrateFamily(row, counters)
            ));
            afterId = rows.get(rows.size() - 1).getFamilyId();
        }
    }

    private void migrateUser(PiiMigrationUser row, Counters counters) {
        counters.userScanned++;
        int populated = populated(
                row.getEmailEncrypted(), row.getEmailHmac(), row.getPhoneEncrypted(),
                row.getPhoneHmac(), row.getUserNameEncrypted()
        );
        if (populated != 0 && populated != 5) {
            throw new IllegalStateException("partially migrated user row detected");
        }

        if (populated == 5) {
            verifyUser(row);
            counters.userVerified++;
            return;
        }

        if (row.getEmail() == null || row.getPhone() == null || row.getUserName() == null) {
            throw new IllegalStateException("unmigrated user row has missing plaintext");
        }
        UserVO protectedUser = new UserVO();
        protectedUser.setEmail(row.getEmail());
        protectedUser.setPhone(row.getPhone());
        protectedUser.setUserName(row.getUserName());
        protectionService.protect(protectedUser);
        rejectCollision(row.getUserId(), protectedUser.getEmailHmac(), protectedUser.getPhoneHmac());
        row.setEmailEncrypted(protectedUser.getEmailEncrypted());
        row.setEmailHmac(protectedUser.getEmailHmac());
        row.setPhoneEncrypted(protectedUser.getPhoneEncrypted());
        row.setPhoneHmac(protectedUser.getPhoneHmac());
        row.setUserNameEncrypted(protectedUser.getUserNameEncrypted());
        if (mapper.updateUser(row) != 1) {
            throw new IllegalStateException("user row changed during migration");
        }
        counters.userMigrated++;
    }

    private void migrateFamily(PiiMigrationFamily row, Counters counters) {
        counters.familyScanned++;
        int populated = populated(row.getFamilyNameEncrypted(), row.getBirthDateEncrypted());
        if (populated != 0 && populated != 2) {
            throw new IllegalStateException("partially migrated family row detected");
        }

        if (populated == 2) {
            String familyName = protectionService.decryptFamilyName(row.getFamilyNameEncrypted());
            java.time.LocalDate birthDate = protectionService.decryptFamilyBirthDate(
                    row.getBirthDateEncrypted()
            );
            if ((row.getFamilyName() != null && !row.getFamilyName().trim().equals(familyName))
                    || (row.getBirthDate() != null && !row.getBirthDate().equals(birthDate))) {
                throw new IllegalStateException("family row verification failed");
            }
            counters.familyVerified++;
            return;
        }

        if (row.getFamilyName() == null || row.getBirthDate() == null) {
            throw new IllegalStateException("unmigrated family row has missing plaintext");
        }
        RecipientVO protectedFamily = new RecipientVO();
        protectedFamily.setFamilyName(row.getFamilyName());
        protectedFamily.setBirthDate(row.getBirthDate());
        protectionService.protect(protectedFamily);
        row.setFamilyNameEncrypted(protectedFamily.getFamilyNameEncrypted());
        row.setBirthDateEncrypted(protectedFamily.getBirthDateEncrypted());
        if (mapper.updateFamily(row) != 1) {
            throw new IllegalStateException("family row changed during migration");
        }
        counters.familyMigrated++;
    }

    private void verifyUser(PiiMigrationUser row) {
        String email = protectionService.decryptEmail(row.getEmailEncrypted());
        String phone = protectionService.decryptPhone(row.getPhoneEncrypted());
        String userName = protectionService.decryptUserName(row.getUserNameEncrypted());
        if (!protectionService.emailLookup(email).equals(row.getEmailHmac())
                || !protectionService.phoneLookup(phone).equals(row.getPhoneHmac())
                || (row.getEmail() != null
                    && !row.getEmail().trim().toLowerCase(Locale.ROOT).equals(email))
                || (row.getPhone() != null && !row.getPhone().trim().equals(phone))
                || (row.getUserName() != null && !row.getUserName().trim().equals(userName))) {
            throw new IllegalStateException("user row verification failed");
        }
        rejectCollision(row.getUserId(), row.getEmailHmac(), row.getPhoneHmac());
    }

    private void rejectCollision(long userId, String emailHmac, String phoneHmac) {
        if (mapper.countEmailHmacCollision(userId, emailHmac) > 0) {
            throw new IllegalStateException("normalized email HMAC collision detected");
        }
        if (mapper.countPhoneHmacCollision(userId, phoneHmac) > 0) {
            throw new IllegalStateException("normalized phone HMAC collision detected");
        }
    }

    private int populated(String... values) {
        int count = 0;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static final class Counters {
        private long userScanned;
        private long userMigrated;
        private long userVerified;
        private long userFailed;
        private long familyScanned;
        private long familyMigrated;
        private long familyVerified;
        private long familyFailed;

        private PiiMigrationReport report() {
            return new PiiMigrationReport(
                    userScanned, userMigrated, userVerified, userFailed,
                    familyScanned, familyMigrated, familyVerified, familyFailed
            );
        }
    }
}
