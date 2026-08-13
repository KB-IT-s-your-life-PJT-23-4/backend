package com.example.project.batch.pii;

import com.example.project.user.crypto.UserPiiCryptoService;
import com.example.project.user.crypto.UserPiiHmacService;
import com.example.project.user.crypto.UserPiiKeyProvider;
import com.example.project.user.crypto.UserPiiProtectionService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PiiMigrationServiceTest {

    @Test
    void migratesOnceAndVerifiesCompletedRowsOnRerun() {
        FakeMapper mapper = new FakeMapper();
        mapper.users.add(user(1L));
        mapper.families.add(family(1L));
        PiiMigrationService service = service(mapper);

        PiiMigrationReport first = service.migrate(100);
        assertEquals(1, first.userMigrated());
        assertEquals(1, first.familyMigrated());
        assertEquals(0, first.userFailed());
        assertNotNull(mapper.users.get(0).getEmailEncrypted());
        assertNotNull(mapper.families.get(0).getBirthDateEncrypted());

        PiiMigrationReport second = service.migrate(100);
        assertEquals(0, second.userMigrated());
        assertEquals(1, second.userVerified());
        assertEquals(0, second.familyMigrated());
        assertEquals(1, second.familyVerified());
    }

    @Test
    void rejectsPartiallyMigratedRows() {
        FakeMapper mapper = new FakeMapper();
        PiiMigrationUser user = user(1L);
        user.setEmailEncrypted("partial");
        mapper.users.add(user);

        assertThrows(IllegalStateException.class, () -> service(mapper).migrate(100));
    }

    private PiiMigrationService service(FakeMapper mapper) {
        UserPiiKeyProvider keys = new UserPiiKeyProvider("v1", key('a'), key('b'));
        UserPiiProtectionService protection = new UserPiiProtectionService(
                new UserPiiCryptoService(keys), new UserPiiHmacService(keys)
        );
        return new PiiMigrationService(
                mapper,
                protection,
                new TransactionTemplate(new ResourcelessTransactionManager())
        );
    }

    private PiiMigrationUser user(long id) {
        PiiMigrationUser user = new PiiMigrationUser();
        user.setUserId(id);
        user.setEmail("User@Example.com");
        user.setPhone("010-1234-5678");
        user.setUserName("User Name");
        return user;
    }

    private PiiMigrationFamily family(long id) {
        PiiMigrationFamily family = new PiiMigrationFamily();
        family.setFamilyId(id);
        family.setFamilyName("Beneficiary");
        family.setBirthDate(LocalDate.of(2008, 2, 29));
        return family;
    }

    private String key(char value) {
        return Base64.getEncoder().encodeToString(
                String.valueOf(value).repeat(32).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static final class FakeMapper implements PiiMigrationMapper {
        private final List<PiiMigrationUser> users = new ArrayList<>();
        private final List<PiiMigrationFamily> families = new ArrayList<>();

        @Override
        public List<PiiMigrationUser> selectUsersAfter(long afterId, int limit) {
            return users.stream().filter(row -> row.getUserId() > afterId)
                    .sorted(Comparator.comparing(PiiMigrationUser::getUserId))
                    .limit(limit).toList();
        }

        @Override
        public List<PiiMigrationFamily> selectFamiliesAfter(long afterId, int limit) {
            return families.stream().filter(row -> row.getFamilyId() > afterId)
                    .sorted(Comparator.comparing(PiiMigrationFamily::getFamilyId))
                    .limit(limit).toList();
        }

        @Override
        public int countEmailHmacCollision(long userId, String emailHmac) {
            return (int) users.stream().filter(row -> row.getUserId() != userId)
                    .filter(row -> emailHmac.equals(row.getEmailHmac())).count();
        }

        @Override
        public int countPhoneHmacCollision(long userId, String phoneHmac) {
            return (int) users.stream().filter(row -> row.getUserId() != userId)
                    .filter(row -> phoneHmac.equals(row.getPhoneHmac())).count();
        }

        @Override
        public int updateUser(PiiMigrationUser user) {
            return 1;
        }

        @Override
        public int updateFamily(PiiMigrationFamily family) {
            return 1;
        }
    }
}
