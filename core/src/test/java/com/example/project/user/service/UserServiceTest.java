package com.example.project.user.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.common.file.ProfileImageStorageService;
import com.example.project.user.domain.UserVO;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserSignupRequest;
import com.example.project.user.dto.request.UserProfileUpdateRequest;
import com.example.project.user.dto.request.UserUpdateRequest;
import com.example.project.user.mapper.AccountStatusMapper;
import com.example.project.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

    @TempDir
    Path tempDirectory;

    private FakeUserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userMapper = new FakeUserMapper();
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userMapper, passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 시 이메일을 정규화하고 비밀번호를 암호화한다")
    void signup() {
        UserSignupRequest request = new UserSignupRequest(
                " User@Example.com ",
                "password123!",
                " 홍길동 ",
                LocalDate.of(1990, 1, 1),
                " 010-1234-5678 ",
                " profile.png "
        );

        UserDTO result = userService.signup(request);

        assertEquals(1L, result.userId());
        assertEquals("user@example.com", result.email());
        assertEquals("홍길동", result.name());
        assertEquals(LocalDate.of(1990, 1, 1), result.birthDate());
        assertEquals("010-1234-5678", result.phone());
        assertEquals("profile.png", result.img());
        assertNotEquals(request.password(), userMapper.savedUser.getPassword());
        assertTrue(passwordEncoder.matches(request.password(), userMapper.savedUser.getPassword()));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 회원가입에 실패한다")
    void signupWithDuplicateEmail() {
        userMapper.savedUser = createUser("user@example.com");
        UserSignupRequest request = new UserSignupRequest(
                "USER@example.com",
                "password123!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "010-1234-5678",
                null
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.signup(request)
        );

        assertEquals(ResponseCode.DUPLICATE_DATA, exception.getResponseCode());
        assertEquals(0, userMapper.insertCount);
    }

    @Test
    @DisplayName("이메일 사용 가능 여부를 반환한다")
    void checkEmailAvailability() {
        assertTrue(userService.checkEmailAvailability("new@example.com").available());

        userMapper.savedUser = createUser("user@example.com");

        assertFalse(userService.checkEmailAvailability("USER@example.com").available());
    }

    @Test
    @DisplayName("인증된 회원의 정보를 조회한다")
    void getProfile() {
        userMapper.savedUser = createUser("user@example.com");
        userService = userServiceWithAccountRefresh();

        UserDTO result = userService.getProfile(1L);

        assertEquals(1L, result.userId());
        assertEquals("user@example.com", result.email());
        assertEquals("홍길동", result.name());
        assertEquals("010-1111-2222", result.phone());
        assertEquals("USER", result.role());
    }

    @Test
    @DisplayName("회원 정보 조회는 아직 유효한 차단 상태를 유지한다")
    void getProfileWithValidBlockedAccount() {
        userMapper.savedUser = createUser("user@example.com");
        userMapper.savedUser.setAccountStatus("BLOCKED");
        userMapper.savedUser.setBlockedUntil(NOW.plusDays(1));
        userService = userServiceWithAccountRefresh();

        UserDTO result = userService.getProfile(1L);

        assertEquals("BLOCKED", result.accountStatus());
        assertEquals(NOW.plusDays(1), result.blockedUntil());
    }

    @Test
    @DisplayName("회원 정보 조회는 만료된 차단을 ACTIVE 상태로 복구한다")
    void getProfileWithExpiredBlockedAccount() {
        userMapper.savedUser = createUser("user@example.com");
        userMapper.savedUser.setAccountStatus("BLOCKED");
        userMapper.savedUser.setBlockedUntil(NOW.minusSeconds(1));
        userService = userServiceWithAccountRefresh();

        UserDTO result = userService.getProfile(1L);

        assertEquals("ACTIVE", result.accountStatus());
        assertNull(result.blockedUntil());
    }

    @Test
    @DisplayName("존재하지 않는 회원의 정보를 조회하면 실패한다")
    void getProfileWithMissingUser() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.getProfile(1L)
        );

        assertEquals(ResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("회원 정보를 수정할 때 이메일을 정규화하고 이름을 반영한다")
    void updateProfile() {
        userMapper.savedUser = createUser("user@example.com");
        UserUpdateRequest request = new UserUpdateRequest(
                " New@Example.com ",
                "김길동",
                LocalDate.of(1991, 2, 2),
                " 010-9999-8888 ",
                " updated.png "
        );

        UserDTO result = userService.updateProfile(1L, request);

        assertEquals("new@example.com", result.email());
        assertEquals("김길동", result.name());
        assertEquals(LocalDate.of(1991, 2, 2), result.birthDate());
        assertEquals("010-9999-8888", result.phone());
        assertEquals("updated.png", result.img());
        assertEquals(1, userMapper.updateCount);
    }

    @Test
    @DisplayName("다른 회원이 사용 중인 이메일로 수정하면 실패한다")
    void updateProfileWithDuplicateEmail() {
        userMapper.savedUser = createUser("user@example.com");
        userMapper.userWithDuplicateEmail = new UserVO(
                2L,
                "duplicate@example.com",
                passwordEncoder.encode("password123!"),
                "김길동",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        UserUpdateRequest request = new UserUpdateRequest(
                "DUPLICATE@example.com",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "010-1234-5678",
                null
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.updateProfile(1L, request)
        );

        assertEquals(ResponseCode.DUPLICATE_DATA, exception.getResponseCode());
        assertEquals(0, userMapper.updateCount);
    }

    @Test
    @DisplayName("프로필 사진 수정은 이메일을 유지하고 등록·삭제 상태를 구분한다")
    void updateEditableProfileImage() {
        userMapper.savedUser = createUser("user@example.com");
        ProfileImageStorageService storage = new ProfileImageStorageService(tempDirectory.toString(), 1024);
        userService = new UserService(userMapper, passwordEncoder, storage);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "김길동",
                LocalDate.of(1991, 2, 2),
                "010-9999-8888"
        );
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.png",
                "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0}
        );

        UserDTO updated = userService.updateEditableProfile(1L, request, image, false);
        UserDTO removed = userService.updateEditableProfile(1L, request, null, true);

        assertEquals("user@example.com", updated.email());
        assertTrue(updated.img().startsWith(ProfileImageStorageService.PUBLIC_PATH_PREFIX));
        assertNull(removed.img());
    }

    @Test
    @DisplayName("멀티파트 프로필 수정 중 중복키가 발생하면 중복 데이터 오류로 처리한다")
    void updateEditableProfileWithDuplicateData() {
        userMapper.savedUser = createUser("user@example.com");
        userMapper.throwDuplicateOnUpdate = true;
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "김길동",
                LocalDate.of(1991, 2, 2),
                "010-9999-8888"
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.updateEditableProfile(1L, request, null, false)
        );

        assertEquals(ResponseCode.DUPLICATE_DATA, exception.getResponseCode());
    }

    @Test
    @DisplayName("회원 탈퇴 시 인증된 회원 정보를 삭제한다")
    void deleteUser() {
        userMapper.savedUser = createUser("user@example.com");

        userService.deleteUser(1L);

        assertEquals(1, userMapper.deleteCount);
        assertNull(userMapper.savedUser);
    }

    @Test
    @DisplayName("존재하지 않는 회원이 탈퇴를 요청하면 실패한다")
    void deleteMissingUser() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.deleteUser(1L)
        );

        assertEquals(ResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
        assertEquals(0, userMapper.deleteCount);
    }

    @Test
    @DisplayName("회원 삭제 건수가 1건이 아니면 데이터베이스 오류로 처리한다")
    void deleteUserWithDatabaseError() {
        userMapper.savedUser = createUser("user@example.com");
        userMapper.deleteResult = 0;

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.deleteUser(1L)
        );

        assertEquals(ResponseCode.DATABASE_ERROR, exception.getResponseCode());
        assertEquals(1, userMapper.deleteCount);
    }

    private UserVO createUser(String email) {
        return new UserVO(
                1L,
                email,
                passwordEncoder.encode("password123!"),
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "010-1111-2222",
                "USER",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "profile.png"
        );
    }

    private UserService userServiceWithAccountRefresh() {
        AccountStatusMapper accountStatusMapper = new AccountStatusMapper() {
            @Override
            public int activateExpiredBlock(Long userId) {
                UserVO user = userMapper.findById(userId);
                if (user != null
                        && "BLOCKED".equals(user.getAccountStatus())
                        && user.getBlockedUntil() != null
                        && !user.getBlockedUntil().isAfter(NOW)) {
                    user.setAccountStatus("ACTIVE");
                    user.setBlockedUntil(null);
                    return 1;
                }
                return 0;
            }

            @Override
            public int activateAllExpiredBlocks() {
                return 0;
            }
        };
        AccountAccessService accountAccessService = new AccountAccessService(
                accountStatusMapper,
                userMapper,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
        return new UserService(userMapper, passwordEncoder, null, accountAccessService);
    }

    private static class FakeUserMapper implements UserMapper {

        private UserVO savedUser;
        private UserVO userWithDuplicateEmail;
        private int insertCount;
        private int updateCount;
        private int deleteCount;
        private int deleteResult = 1;
        private boolean throwDuplicateOnUpdate;

        @Override
        public UserVO findById(Long userId) {
            if (savedUser == null || !savedUser.getUserId().equals(userId)) {
                return null;
            }
            return savedUser;
        }

        @Override
        public UserVO findByEmail(String email) {
            if (savedUser != null && savedUser.getEmail().equals(email)) {
                return savedUser;
            }

            if (userWithDuplicateEmail != null && userWithDuplicateEmail.getEmail().equals(email)) {
                return userWithDuplicateEmail;
            }

            return null;
        }

        @Override
        public int insert(UserVO user) {
            insertCount++;
            user.setUserId(1L);
            if (user.getRole() == null) {
                user.setRole("USER");
            }
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            savedUser = user;
            return 1;
        }

        @Override
        public int update(UserVO user) {
            updateCount++;
            if (throwDuplicateOnUpdate) {
                throw new DuplicateKeyException("duplicate user data");
            }
            user.setUpdatedAt(LocalDateTime.now());
            savedUser = user;
            return 1;
        }

        @Override
        public int deleteById(Long userId) {
            deleteCount++;

            if (deleteResult == 1
                    && savedUser != null
                    && savedUser.getUserId().equals(userId)) {
                savedUser = null;
                return 1;
            }

            return 0;
        }
    }
}
