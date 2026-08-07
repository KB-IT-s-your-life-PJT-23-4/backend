package com.example.project.admin.auth.service;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuthorizationServiceTest {

    private FakeUserMapper userMapper;
    private AdminAuthorizationService service;

    @BeforeEach
    void setUp() {
        userMapper = new FakeUserMapper();
        service = new AdminAuthorizationService(userMapper);
    }

    @Test
    @DisplayName("ROOT, MIDDLE, DEFAULT 역할만 관리자로 판단한다")
    void identifyExactAdminRoles() {
        assertTrue(service.isAdminRole("ROOT"));
        assertTrue(service.isAdminRole("MIDDLE"));
        assertTrue(service.isAdminRole("DEFAULT"));
        assertFalse(service.isAdminRole("USER"));
        assertFalse(service.isAdminRole(null));
        assertFalse(service.isAdminRole("root"));
        assertFalse(service.isAdminRole(" ROOT"));
        assertFalse(service.isAdminRole("UNKNOWN"));
    }

    @Test
    @DisplayName("인증된 사용자 ID로 DB의 현재 역할을 조회한다")
    void loadCurrentDatabaseRole() {
        userMapper.save(user(1L, "MIDDLE"));

        AdminPrincipal principal = service.findCurrentUser("1").orElseThrow();

        assertEquals(1L, principal.userId());
        assertEquals("MIDDLE", principal.role());
    }

    @Test
    @DisplayName("존재하지 않거나 올바르지 않은 사용자 ID는 인증 사용자로 반환하지 않는다")
    void rejectMissingOrInvalidUser() {
        assertTrue(service.findCurrentUser("99").isEmpty());
        assertTrue(service.findCurrentUser("not-a-number").isEmpty());
        assertTrue(service.findCurrentUser(null).isEmpty());
    }

    @Test
    @DisplayName("필터가 만든 DB 기반 관리자 principal만 현재 관리자로 반환한다")
    void returnCurrentAdminFromDatabasePrincipal() {
        var authentication = new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(7L, "DEFAULT"),
                null,
                java.util.List.of()
        );

        var response = service.getCurrentAdmin(authentication);

        assertEquals(7L, response.userId());
        assertEquals("DEFAULT", response.role());
    }

    @Test
    @DisplayName("일반 문자열 principal이나 USER 역할은 현재 관리자로 허용하지 않는다")
    void rejectUntrustedOrUserPrincipal() {
        var untrusted = new UsernamePasswordAuthenticationToken("1", null, java.util.List.of());
        ServiceException unauthorized = assertThrows(
                ServiceException.class,
                () -> service.getCurrentAdmin(untrusted)
        );
        assertEquals(ResponseCode.UNAUTHORIZED, unauthorized.getResponseCode());

        var user = new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(1L, "USER"),
                null,
                java.util.List.of()
        );
        ServiceException forbidden = assertThrows(
                ServiceException.class,
                () -> service.getCurrentAdmin(user)
        );
        assertEquals(ResponseCode.FORBIDDEN, forbidden.getResponseCode());
    }

    private UserVO user(Long userId, String role) {
        UserVO user = new UserVO();
        user.setUserId(userId);
        user.setRole(role);
        return user;
    }

    private static final class FakeUserMapper implements UserMapper {

        private final Map<Long, UserVO> users = new HashMap<>();

        private void save(UserVO user) {
            users.put(user.getUserId(), user);
        }

        @Override
        public UserVO findById(Long userId) {
            return users.get(userId);
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
