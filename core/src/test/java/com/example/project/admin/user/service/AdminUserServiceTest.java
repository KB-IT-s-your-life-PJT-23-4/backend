package com.example.project.admin.user.service;

import com.example.project.admin.user.domain.AdminUserRecord;
import com.example.project.admin.user.mapper.AdminUserMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.mapper.UserMapper;
import com.example.project.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminUserServiceTest {

    private FakeAdminUserMapper mapper;
    private FakeUserService userService;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeAdminUserMapper();
        userService = new FakeUserService();
        service = new AdminUserService(mapper, userService);
    }

    @Test
    @DisplayName("이메일·이름·회원 ID 검색 조건과 페이지 정보를 전달하고 개인정보를 마스킹한다")
    void searchUsersAndMaskPersonalInformation() {
        mapper.totalElements = 21L;
        mapper.users.add(user(7L, "tester@example.com", "홍길동", 2L, 3L, 4L));

        var response = service.getUsers(7L, " TESTER@EXAMPLE.COM ", " 홍길동 ", 1, 20);
        var user = response.getUsers().get(0);

        assertEquals(7L, mapper.userId);
        assertEquals("tester@example.com", mapper.email);
        assertEquals("홍길동", mapper.name);
        assertEquals(20L, mapper.offset);
        assertEquals(20, mapper.size);
        assertEquals("t***@example.com", user.getEmail());
        assertEquals("홍*동", user.getName());
        assertNull(user.getAccountStatus());
        assertFalse(user.isAccountStatusAvailable());
        assertEquals(2L, user.getRecipientCount());
        assertEquals(3L, user.getGiftCount());
        assertEquals(4L, user.getSimulationCount());
        assertEquals(2, response.getPagination().getTotalPages());
        assertFalse(response.getPagination().isFirst());
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
