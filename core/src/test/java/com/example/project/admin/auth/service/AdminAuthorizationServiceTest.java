package com.example.project.admin.auth.service;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.auth.dto.request.AdminAuthCreateRequest;
import com.example.project.admin.auth.dto.request.AdminChangeAuthRequest;
import com.example.project.admin.auth.dto.response.AdminAuthPageResponse;
import com.example.project.admin.auth.dto.response.AdminAuthResponse;
import com.example.project.admin.auth.mapper.AdminAuthMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuthorizationServiceTest {

    private FakeUserMapper userMapper;
    private FakeAdminAuthMapper adminAuthMapper;
    private AdminAuthorizationService service;

    @BeforeEach
    void setUp() {
        userMapper = new FakeUserMapper();
        adminAuthMapper = new FakeAdminAuthMapper();
        service = new AdminAuthorizationService(
                userMapper,
                adminAuthMapper,
                new BCryptPasswordEncoder(4)
        );
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

    @Test
    @DisplayName("관리자 역할 계정만 페이지 응답으로 반환한다")
    void returnOnlyAdminRoleAccounts() {
        adminAuthMapper.totalAdmins = 3L;
        adminAuthMapper.admins = List.of(
                user(1L, "ROOT"),
                user(2L, "USER")
        );

        AdminAuthPageResponse response = service.getAdmins(1, 2);

        assertEquals(Set.of("ROOT", "MIDDLE", "DEFAULT"), adminAuthMapper.roles);
        assertEquals(2L, adminAuthMapper.offset);
        assertEquals(2, adminAuthMapper.size);
        assertEquals(1, response.getAdmins().size());
        assertTrue(response.getAdmins().stream()
                .noneMatch(admin -> "USER".equals(admin.getRole())));
        assertEquals(3L, response.getPagination().getTotalElements());
        assertEquals(2, response.getPagination().getTotalPages());
        assertTrue(response.getPagination().isLast());
    }

    @Test
    @DisplayName("ROOT 관리자가 대상 사용자의 관리자 역할을 변경할 수 있도록 값을 정규화한다")
    void changeAdminRole() {
        adminAuthMapper.save(user(10L, "USER"));

        service.changeAuth(
                10L,
                AdminChangeAuthRequest.builder().role(" middle ").build()
        );

        assertEquals(10L, adminAuthMapper.changedUserId);
        assertEquals("MIDDLE", adminAuthMapper.changedRole);
    }

    @Test
    @DisplayName("허용되지 않은 역할이나 존재하지 않는 사용자의 권한은 변경하지 않는다")
    void rejectInvalidAdminRoleChange() {
        adminAuthMapper.save(user(10L, "USER"));

        ServiceException invalidRole = assertThrows(
                ServiceException.class,
                () -> service.changeAuth(
                        10L,
                        AdminChangeAuthRequest.builder().role("USER").build()
                )
        );
        assertEquals(ResponseCode.BAD_REQUEST, invalidRole.getResponseCode());

        ServiceException missingUser = assertThrows(
                ServiceException.class,
                () -> service.changeAuth(
                        999L,
                        AdminChangeAuthRequest.builder().role("DEFAULT").build()
                )
        );
        assertEquals(ResponseCode.MEMBER_NOT_FOUND, missingUser.getResponseCode());
        assertEquals(0, adminAuthMapper.changeCalls);
    }

    @Test
    @DisplayName("관리자 삭제는 관리자 계정을 실제로 삭제한다")
    void deleteAdmin() {
        adminAuthMapper.save(user(10L, "DEFAULT"));

        service.deleteAdmin(10L);

        assertEquals(10L, adminAuthMapper.deletedUserId);
        assertEquals(1, adminAuthMapper.deleteCalls);
        assertTrue(adminAuthMapper.findByUserId(10L).isEmpty());
    }

    @Test
    @DisplayName("관리자 역할이 아닌 사용자의 관리자 권한은 삭제할 수 없다")
    void rejectDeletingNonAdminRole() {
        adminAuthMapper.save(user(10L, "USER"));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.deleteAdmin(10L)
        );

        assertEquals(ResponseCode.RESOURCE_NOT_FOUND, exception.getResponseCode());
        assertEquals(0, adminAuthMapper.deleteCalls);
    }

    @Test
    @DisplayName("신규 관리자 계정은 정규화된 정보와 암호화된 비밀번호로 생성한다")
    void createAdmin() {
        AdminAuthResponse response = service.createAdmin(
                AdminAuthCreateRequest.builder()
                        .email(" ADMIN@Example.com ")
                        .password("Admin1234!")
                        .name("관리자")
                        .phone("010-1234-5678")
                        .role(" middle ")
                        .build()
        );

        UserVO created = adminAuthMapper.findByUserId(response.getAdminId()).orElseThrow();
        assertEquals("admin@example.com", created.getEmail());
        assertEquals("관리자", created.getUserName());
        assertEquals("010-1234-5678", created.getPhone());
        assertEquals("MIDDLE", created.getRole());
        assertTrue(new BCryptPasswordEncoder().matches("Admin1234!", created.getPassword()));
        assertEquals(created.getUserId().longValue(), response.getAdminId());
    }

    @Test
    @DisplayName("중복 이메일이나 관리자 이외 역할로 계정을 생성할 수 없다")
    void rejectInvalidAdminCreation() {
        UserVO existing = user(20L, "USER");
        existing.setEmail("existing@example.com");
        userMapper.save(existing);

        ServiceException duplicate = assertThrows(
                ServiceException.class,
                () -> service.createAdmin(adminRequest("existing@example.com", "DEFAULT"))
        );
        assertEquals(ResponseCode.DUPLICATE_DATA, duplicate.getResponseCode());

        ServiceException invalidRole = assertThrows(
                ServiceException.class,
                () -> service.createAdmin(adminRequest("new@example.com", "USER"))
        );
        assertEquals(ResponseCode.BAD_REQUEST, invalidRole.getResponseCode());
    }

    private AdminAuthCreateRequest adminRequest(String email, String role) {
        return AdminAuthCreateRequest.builder()
                .email(email)
                .password("Admin1234!")
                .name("관리자")
                .phone("010-9876-5432")
                .role(role)
                .build();
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
            return users.values().stream()
                    .filter(user -> email.equals(user.getEmail()))
                    .findFirst()
                    .orElse(null);
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

    private static final class FakeAdminAuthMapper implements AdminAuthMapper {

        private List<UserVO> admins = List.of();
        private long totalAdmins;
        private Set<String> roles;
        private long offset;
        private int size;
        private final Map<Long, UserVO> users = new HashMap<>();
        private int changeCalls;
        private int deleteCalls;
        private Long changedUserId;
        private String changedRole;
        private Long deletedUserId;
        private long sequence = 100L;

        private void save(UserVO user) {
            users.put(user.getUserId(), user);
        }

        @Override
        public List<UserVO> getAdmins(
                Set<String> roles,
                long offset,
                int size
        ) {
            this.roles = Set.copyOf(roles);
            this.offset = offset;
            this.size = size;
            return admins;
        }

        @Override
        public long getAdminCounts(Set<String> roles) {
            this.roles = Set.copyOf(roles);
            return totalAdmins;
        }

        @Override
        public Optional<UserVO> findByUserId(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public int changeAuth(Long userId, String role) {
            changeCalls++;
            changedUserId = userId;
            changedRole = role;
            UserVO user = users.get(userId);
            if (user == null) {
                return 0;
            }
            user.setRole(role);
            return 1;
        }

        @Override
        public int deleteAdmin(Long userId) {
            deleteCalls++;
            deletedUserId = userId;
            UserVO user = users.get(userId);
            if (user == null || !Set.of("ROOT", "MIDDLE", "DEFAULT").contains(user.getRole())) {
                return 0;
            }
            users.remove(userId);
            return 1;
        }

        @Override
        public int createAdmin(UserVO admin) {
            admin.setUserId(++sequence);
            users.put(admin.getUserId(), admin);
            return 1;
        }
    }
}
