package com.example.project.security;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAccessValidatorTest {

    private final AdminAccessValidator validator = new AdminAccessValidator();

    @Test
    @DisplayName("DB 기반 ROOT principal은 ROOT 전용 작업을 수행할 수 있다")
    void allowRootPrincipal() {
        AdminPrincipal principal = new AdminPrincipal(1L, "ROOT");
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );

        assertSame(principal, validator.requireRoot(authentication));
    }

    @Test
    @DisplayName("ROOT가 아닌 관리자는 ROOT 전용 작업을 수행할 수 없다")
    void rejectNonRootPrincipal() {
        var authentication = new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(2L, "MIDDLE"),
                null,
                List.of()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> validator.requireRoot(authentication)
        );
        assertEquals(ResponseCode.FORBIDDEN, exception.getResponseCode());
    }

    @Test
    @DisplayName("MIDDLE과 DEFAULT 역할을 각각 검증할 수 있다")
    void validateMiddleAndDefaultRoles() {
        AdminPrincipal middlePrincipal = new AdminPrincipal(2L, "MIDDLE");
        var middleAuthentication = authentication(middlePrincipal);
        AdminPrincipal defaultPrincipal = new AdminPrincipal(3L, "DEFAULT");
        var defaultAuthentication = authentication(defaultPrincipal);

        assertSame(middlePrincipal, validator.requireMiddle(middleAuthentication));
        assertSame(defaultPrincipal, validator.requireDefault(defaultAuthentication));
        assertThrows(
                ServiceException.class,
                () -> validator.requireMiddle(defaultAuthentication)
        );
    }

    @Test
    @DisplayName("ROOT 또는 MIDDLE 권한과 전체 관리자 권한을 묶어서 검증할 수 있다")
    void validateAdminRoleGroups() {
        var rootAuthentication = authentication(new AdminPrincipal(1L, "ROOT"));
        var middleAuthentication = authentication(new AdminPrincipal(2L, "MIDDLE"));
        var defaultAuthentication = authentication(new AdminPrincipal(3L, "DEFAULT"));

        validator.requireRootOrMiddle(rootAuthentication);
        validator.requireRootOrMiddle(middleAuthentication);
        validator.requireAdmin(defaultAuthentication);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> validator.requireRootOrMiddle(defaultAuthentication)
        );
        assertEquals(ResponseCode.FORBIDDEN, exception.getResponseCode());
    }

    @Test
    @DisplayName("지원하지 않는 관리자 역할은 검증 조건으로 지정할 수 없다")
    void rejectUnsupportedAllowedRole() {
        var authentication = authentication(new AdminPrincipal(1L, "ROOT"));

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.requireAnyRole(authentication, "USER")
        );
    }

    @Test
    @DisplayName("신뢰할 수 없는 principal은 ROOT 인증으로 사용하지 않는다")
    void rejectUntrustedPrincipal() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "1",
                null,
                List.of()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> validator.requireRoot(authentication)
        );
        assertEquals(ResponseCode.UNAUTHORIZED, exception.getResponseCode());
    }

    private UsernamePasswordAuthenticationToken authentication(AdminPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );
    }
}
