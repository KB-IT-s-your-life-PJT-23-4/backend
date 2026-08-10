package com.example.project.user.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.AccountStatusMapper;
import com.example.project.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountAccessServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 8, 12, 0);

    private UserVO user;
    private FakeAccountStatusMapper accountStatusMapper;
    private AccountAccessService service;

    @BeforeEach
    void setUp() {
        user = new UserVO();
        user.setUserId(7L);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        accountStatusMapper = new FakeAccountStatusMapper(user, NOW);
        service = new AccountAccessService(
                accountStatusMapper,
                new FakeUserMapper(user),
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("활성 회원은 제한 기능에 접근할 수 있다")
    void allowActiveAccount() {
        assertDoesNotThrow(() -> service.requireRestrictedFeatureAccess(7L));
    }

    @Test
    @DisplayName("차단 기한이 남은 회원은 제한 기능에 접근할 수 없다")
    void rejectCurrentlyBlockedAccount() {
        user.setAccountStatus("BLOCKED");
        user.setBlockedUntil(NOW.plusDays(1));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.requireRestrictedFeatureAccess(7L)
        );

        assertEquals(ResponseCode.FORBIDDEN, exception.getResponseCode());
    }

    @Test
    @DisplayName("만료된 차단은 조건부 갱신으로 한 번만 해제되고 접근이 허용된다")
    void lazilyActivateExpiredBlockOnlyOnce() {
        user.setAccountStatus("BLOCKED");
        user.setBlockedUntil(NOW.minusSeconds(1));

        assertDoesNotThrow(() -> service.requireRestrictedFeatureAccess(7L));
        assertDoesNotThrow(() -> service.requireRestrictedFeatureAccess(7L));

        assertEquals("ACTIVE", user.getAccountStatus());
        assertNull(user.getBlockedUntil());
        assertEquals(1, accountStatusMapper.updateCount);
    }

    @Test
    @DisplayName("관리자 목록 조회 전에는 만료 차단 전체를 한 번에 복구한다")
    void activateAllExpiredBlocks() {
        accountStatusMapper.allExpiredUpdateCount = 2;

        assertEquals(2, service.refreshAllExpiredBlocks());
        assertEquals(1, accountStatusMapper.activateAllCallCount);
    }

    private static final class FakeAccountStatusMapper implements AccountStatusMapper {

        private final UserVO user;
        private final LocalDateTime now;
        private int updateCount;
        private int allExpiredUpdateCount;
        private int activateAllCallCount;

        private FakeAccountStatusMapper(UserVO user, LocalDateTime now) {
            this.user = user;
            this.now = now;
        }

        @Override
        public int activateExpiredBlock(Long userId) {
            if (userId.equals(user.getUserId())
                    && "BLOCKED".equals(user.getAccountStatus())
                    && user.getBlockedUntil() != null
                    && !user.getBlockedUntil().isAfter(now)) {
                user.setAccountStatus("ACTIVE");
                user.setBlockedUntil(null);
                updateCount++;
                return 1;
            }
            return 0;
        }

        @Override
        public int activateAllExpiredBlocks() {
            activateAllCallCount++;
            return allExpiredUpdateCount;
        }
    }

    private static final class FakeUserMapper implements UserMapper {

        private final UserVO user;

        private FakeUserMapper(UserVO user) {
            this.user = user;
        }

        @Override
        public UserVO findById(Long userId) {
            return user.getUserId().equals(userId) ? user : null;
        }

        @Override
        public UserVO findByEmail(String email) {
            return null;
        }

        @Override
        public int insert(UserVO userVO) {
            return 0;
        }

        @Override
        public int update(UserVO userVO) {
            return 0;
        }

        @Override
        public int deleteById(Long userId) {
            return 0;
        }
    }
}
