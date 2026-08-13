package com.example.project.admin.user.service;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.auth.service.AdminAuthorizationService;
import com.example.project.admin.user.domain.AdminUserRecord;
import com.example.project.admin.user.mapper.AdminUserMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.service.AccountAccessService;
import com.example.project.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminUserServiceTest {

    private FakeAdminUserMapper mapper;
    private FakeUserService userService;
    private AdminUserService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        mapper = new FakeAdminUserMapper();
        userService = new FakeUserService();
        clock = Clock.fixed(
                LocalDateTime.of(2026, 8, 8, 12, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
        service = new AdminUserService(
                mapper,
                userService,
                new AdminAuthorizationService(
                        null, null, null,
                        com.example.project.support.PiiTestSupport.protectionService()
                ),
                new AccountAccessService(
                        null, null, clock,
                        com.example.project.support.PiiTestSupport.protectionService()
                ) {
                    @Override
                    public com.example.project.user.domain.UserVO refreshAndGet(Long userId) {
                        activateIfExpired(mapper.selectUserById(userId));
                        return new com.example.project.user.domain.UserVO();
                    }

                    @Override
                    public int refreshAllExpiredBlocks() {
                        int updated = 0;
                        for (AdminUserRecord user : mapper.users) {
                            updated += activateIfExpired(user);
                        }
                        return updated;
                    }

                    private int activateIfExpired(AdminUserRecord user) {
                        if (user != null
                                && "BLOCKED".equals(user.getAccountStatus())
                                && user.getBlockedUntil() != null
                                && !user.getBlockedUntil().isAfter(LocalDateTime.now(clock))) {
                            user.setAccountStatus("ACTIVE");
                            user.setBlockedUntil(null);
                            return 1;
                        }
                        return 0;
                    }
                },
                clock,
                com.example.project.support.PiiTestSupport.protectionService()
        );
    }

    @Test
    @DisplayName("정확 이메일·회원 ID 검색 조건과 페이지 정보를 전달하고 개인정보를 마스킹한다")
    void searchUsersAndMaskPersonalInformation() {
        mapper.totalElements = 21L;
        mapper.users.add(user(7L, "tester@example.com", "홍길동", 2L, 3L, 4L));

        var response = service.getUsers(7L, " TESTER@EXAMPLE.COM ", null, 1, 20);
        var user = response.getUsers().get(0);

        assertEquals(7L, mapper.userId);
        assertEquals(
                com.example.project.support.PiiTestSupport.emailLookup("tester@example.com"),
                mapper.email
        );
        assertNull(mapper.name);
        assertEquals(20L, mapper.offset);
        assertEquals(20, mapper.size);
        assertEquals("t***@example.com", user.getEmail());
        assertEquals("홍*동", user.getName());
        assertEquals("ACTIVE", user.getAccountStatus());
        assertTrue(user.isAccountStatusAvailable());
        assertNull(user.getBlockedUntil());
        assertEquals(2L, user.getRecipientCount());
        assertEquals(3L, user.getGiftCount());
        assertEquals(4L, user.getSimulationCount());
        assertEquals(2, response.getPagination().getTotalPages());
        assertFalse(response.getPagination().isFirst());
    }

    @Test
    @DisplayName("암호화된 이름에 대한 부분 검색 요청은 명확히 거부한다")
    void rejectNameSearch() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.getUsers(null, null, "홍길동", 0, 10)
        );

        assertEquals(ResponseCode.BAD_REQUEST, exception.getResponseCode());
    }

    @Test
    @DisplayName("회원 목록은 만료된 차단만 해제하고 유효한 차단은 유지한다")
    void refreshExpiredBlocksBeforeListingUsers() {
        AdminUserRecord expired = user(7L, "expired@example.com", "만료회원", 0L, 0L, 0L);
        expired.setAccountStatus("BLOCKED");
        expired.setBlockedUntil(LocalDateTime.of(2026, 8, 8, 11, 59));
        AdminUserRecord blocked = user(8L, "blocked@example.com", "차단회원", 0L, 0L, 0L);
        blocked.setAccountStatus("BLOCKED");
        blocked.setBlockedUntil(LocalDateTime.of(2026, 8, 9, 12, 0));
        mapper.users.add(expired);
        mapper.users.add(blocked);
        mapper.totalElements = 2L;

        var response = service.getUsers(null, null, null, 0, 20);

        assertEquals("ACTIVE", response.getUsers().get(0).getAccountStatus());
        assertNull(response.getUsers().get(0).getBlockedUntil());
        assertEquals("BLOCKED", response.getUsers().get(1).getAccountStatus());
        assertEquals(blocked.getBlockedUntil(), response.getUsers().get(1).getBlockedUntil());
    }

    @Test
    @DisplayName("회원 상세는 만료된 차단 상태를 해제한 뒤 반환한다")
    void refreshExpiredBlockBeforeGettingUser() {
        AdminUserRecord expired = user(7L, "expired@example.com", "만료회원", 0L, 0L, 0L);
        expired.setAccountStatus("BLOCKED");
        expired.setBlockedUntil(LocalDateTime.of(2026, 8, 8, 11, 59));
        mapper.users.add(expired);

        var response = service.getUser(7L);

        assertEquals("ACTIVE", response.getAccountStatus());
        assertNull(response.getBlockedUntil());
    }

    @Test
    @DisplayName("회원 상세가 없으면 기존 회원 없음 응답 코드를 사용한다")
    void rejectMissingUser() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.getUser(99L)
        );
        assertEquals(ResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("잘못된 페이지와 회원 ID는 조회 전에 거부한다")
    void rejectInvalidSearchInput() {
        assertEquals(
                ResponseCode.BAD_REQUEST,
                assertThrows(
                        ServiceException.class,
                        () -> service.getUsers(0L, null, null, 0, 20)
                ).getResponseCode()
        );
        assertEquals(
                ResponseCode.BAD_REQUEST,
                assertThrows(
                        ServiceException.class,
                        () -> service.getUsers(null, null, null, 0, 101)
                ).getResponseCode()
        );
    }

    @Test
    @DisplayName("회원 삭제는 기존 회원탈퇴 서비스를 그대로 재사용한다")
    void deleteUserWithExistingWithdrawalService() {
        service.deleteUser(7L);

        assertEquals(7L, userService.deletedUserId);
    }

    @Test
    @DisplayName("회원 삭제 실패는 기존 회원탈퇴 서비스의 응답 코드를 유지한다")
    void propagateDeleteFailure() {
        userService.deleteFailure = new ServiceException(ResponseCode.MEMBER_NOT_FOUND);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.deleteUser(99L)
        );

        assertEquals(ResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("ROOT와 MIDDLE 관리자는 일반 회원을 미래 시각까지 차단할 수 있다")
    void blockUserByRootAndMiddle() {
        LocalDateTime blockedUntil = LocalDateTime.of(2026, 8, 9, 12, 0);

        for (String role : List.of("ROOT", "MIDDLE")) {
            mapper.users.clear();
            mapper.users.add(user(7L, "tester@example.com", "홍길동", 0L, 0L, 0L));

            var response = service.blockUser(authentication(role), 7L, blockedUntil);

            assertEquals("BLOCKED", response.getAccountStatus());
            assertEquals(blockedUntil, response.getBlockedUntil());
        }
    }

    @Test
    @DisplayName("DEFAULT와 USER 권한은 회원을 차단할 수 없다")
    void rejectUnauthorizedBlockManagerRoles() {
        mapper.users.add(user(7L, "tester@example.com", "홍길동", 0L, 0L, 0L));

        for (String role : List.of("DEFAULT", "USER")) {
            ServiceException exception = assertThrows(
                    ServiceException.class,
                    () -> service.blockUser(
                            authentication(role),
                            7L,
                            LocalDateTime.of(2026, 8, 9, 12, 0)
                    )
            );
            assertEquals(ResponseCode.FORBIDDEN, exception.getResponseCode());
        }
    }

    @Test
    @DisplayName("관리자 계정과 이미 차단된 회원은 차단 대상이 될 수 없다")
    void rejectInvalidBlockTargets() {
        AdminUserRecord admin = user(2L, "admin@example.com", "관리자", 0L, 0L, 0L);
        admin.setRole("DEFAULT");
        mapper.users.add(admin);
        assertEquals(
                ResponseCode.FORBIDDEN,
                assertThrows(
                        ServiceException.class,
                        () -> service.blockUser(
                                authentication("ROOT"),
                                2L,
                                LocalDateTime.of(2026, 8, 9, 12, 0)
                        )
                ).getResponseCode()
        );

        AdminUserRecord blocked = user(7L, "tester@example.com", "홍길동", 0L, 0L, 0L);
        blocked.setAccountStatus("BLOCKED");
        blocked.setBlockedUntil(LocalDateTime.of(2026, 8, 9, 12, 0));
        mapper.users.add(blocked);
        assertEquals(
                ResponseCode.CONFLICT,
                assertThrows(
                        ServiceException.class,
                        () -> service.blockUser(
                                authentication("ROOT"),
                                7L,
                                LocalDateTime.of(2026, 8, 10, 12, 0)
                        )
                ).getResponseCode()
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원 차단은 실패한다")
    void rejectMissingBlockTarget() {
        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.blockUser(
                        authentication("ROOT"),
                        99L,
                        LocalDateTime.of(2026, 8, 9, 12, 0)
                )
        );

        assertEquals(ResponseCode.MEMBER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("과거 또는 현재 시각으로는 회원을 차단할 수 없다")
    void rejectNonFutureBlockExpiration() {
        mapper.users.add(user(7L, "tester@example.com", "홍길동", 0L, 0L, 0L));

        for (LocalDateTime value : List.of(
                LocalDateTime.of(2026, 8, 8, 11, 59),
                LocalDateTime.of(2026, 8, 8, 12, 0)
        )) {
            assertEquals(
                    ResponseCode.BAD_REQUEST,
                    assertThrows(
                            ServiceException.class,
                            () -> service.blockUser(authentication("ROOT"), 7L, value)
                    ).getResponseCode()
            );
        }
    }

    @Test
    @DisplayName("ROOT 또는 MIDDLE 관리자는 차단된 일반 회원을 해제할 수 있다")
    void unblockUser() {
        AdminUserRecord blocked = user(7L, "tester@example.com", "홍길동", 0L, 0L, 0L);
        blocked.setAccountStatus("BLOCKED");
        blocked.setBlockedUntil(LocalDateTime.of(2026, 8, 9, 12, 0));
        mapper.users.add(blocked);

        var response = service.unblockUser(authentication("MIDDLE"), 7L);

        assertEquals("ACTIVE", response.getAccountStatus());
        assertNull(response.getBlockedUntil());
    }

    @Test
    @DisplayName("이미 활성 상태인 회원의 차단 해제는 실패한다")
    void rejectAlreadyActiveUnblock() {
        mapper.users.add(user(7L, "tester@example.com", "홍길동", 0L, 0L, 0L));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.unblockUser(authentication("ROOT"), 7L)
        );

        assertEquals(ResponseCode.CONFLICT, exception.getResponseCode());
    }

    @Test
    @DisplayName("상태 검증 후 Mapper 갱신 건수가 0이면 충돌로 처리한다")
    void rejectUnexpectedZeroUpdateCount() {
        mapper.users.add(user(7L, "tester@example.com", "홍길동", 0L, 0L, 0L));
        mapper.forceBlockResultZero = true;
        assertEquals(
                ResponseCode.CONFLICT,
                assertThrows(
                        ServiceException.class,
                        () -> service.blockUser(
                                authentication("ROOT"),
                                7L,
                                LocalDateTime.of(2026, 8, 9, 12, 0)
                        )
                ).getResponseCode()
        );

        AdminUserRecord blocked = mapper.selectUserById(7L);
        blocked.setAccountStatus("BLOCKED");
        blocked.setBlockedUntil(LocalDateTime.of(2026, 8, 9, 12, 0));
        mapper.forceUnblockResultZero = true;
        assertEquals(
                ResponseCode.CONFLICT,
                assertThrows(
                        ServiceException.class,
                        () -> service.unblockUser(authentication("MIDDLE"), 7L)
                ).getResponseCode()
        );
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(1L, role),
                null,
                List.of()
        );
    }

    private AdminUserRecord user(
            Long userId,
            String email,
            String name,
            Long recipientCount,
            Long giftCount,
            Long simulationCount
    ) {
        AdminUserRecord user = new AdminUserRecord();
        user.setUserId(userId);
        user.setEmail(email);
        user.setUserName(name);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.of(2026, 8, 1, 12, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 8, 2, 12, 0));
        user.setRecipientCount(recipientCount);
        user.setGiftCount(giftCount);
        user.setSimulationCount(simulationCount);
        return user;
    }

    private static final class FakeAdminUserMapper implements AdminUserMapper {

        private final List<AdminUserRecord> users = new ArrayList<>();
        private long totalElements;
        private Long userId;
        private String email;
        private String name;
        private long offset;
        private int size;
        private boolean forceBlockResultZero;
        private boolean forceUnblockResultZero;

        @Override
        public long countUsers(Long userId, String email, String name) {
            this.userId = userId;
            this.email = email;
            this.name = name;
            return totalElements;
        }

        @Override
        public List<AdminUserRecord> selectUsers(
                Long userId,
                String email,
                String name,
                long offset,
                int size
        ) {
            this.offset = offset;
            this.size = size;
            return users;
        }

        @Override
        public AdminUserRecord selectUserById(Long userId) {
            return users.stream()
                    .filter(user -> userId.equals(user.getUserId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int blockUser(Long userId, LocalDateTime blockedUntil) {
            if (forceBlockResultZero) {
                return 0;
            }
            AdminUserRecord user = selectUserById(userId);
            if (user == null || !"USER".equals(user.getRole())
                    || !"ACTIVE".equals(user.getAccountStatus())) {
                return 0;
            }
            user.setAccountStatus("BLOCKED");
            user.setBlockedUntil(blockedUntil);
            return 1;
        }

        @Override
        public int unblockUser(Long userId) {
            if (forceUnblockResultZero) {
                return 0;
            }
            AdminUserRecord user = selectUserById(userId);
            if (user == null || !"USER".equals(user.getRole())
                    || !"BLOCKED".equals(user.getAccountStatus())) {
                return 0;
            }
            user.setAccountStatus("ACTIVE");
            user.setBlockedUntil(null);
            return 1;
        }
    }

    private static final class FakeUserService extends UserService {

        private Long deletedUserId;
        private ServiceException deleteFailure;

        private FakeUserService() {
            super((UserMapper) null, null);
        }

        @Override
        public void deleteUser(Long userId) {
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            deletedUserId = userId;
        }
    }
}
