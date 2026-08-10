package com.example.project.security;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
public class AdminAccessValidator {

    private static final String ROOT_ROLE = "ROOT";
    private static final String MIDDLE_ROLE = "MIDDLE";
    private static final String DEFAULT_ROLE = "DEFAULT";
    private static final Set<String> ADMIN_ROLES = Set.of(
            ROOT_ROLE,
            MIDDLE_ROLE,
            DEFAULT_ROLE
    );

    public AdminPrincipal requireRoot(Authentication authentication) {
        return requireAnyRole(authentication, ROOT_ROLE);
    }

    public AdminPrincipal requireMiddle(Authentication authentication) {
        return requireAnyRole(authentication, MIDDLE_ROLE);
    }

    public AdminPrincipal requireDefault(Authentication authentication) {
        return requireAnyRole(authentication, DEFAULT_ROLE);
    }

    public AdminPrincipal requireRootOrMiddle(Authentication authentication) {
        return requireAnyRole(authentication, ROOT_ROLE, MIDDLE_ROLE);
    }

    public AdminPrincipal requireAdmin(Authentication authentication) {
        return requireAnyRole(authentication, ROOT_ROLE, MIDDLE_ROLE, DEFAULT_ROLE);
    }

    public AdminPrincipal requireAnyRole(
            Authentication authentication,
            String... allowedRoles
    ) {
        validateAllowedRoles(allowedRoles);
        AdminPrincipal principal = requireAdminPrincipal(authentication);

        if (Arrays.stream(allowedRoles).noneMatch(principal.role()::equals)) {
            throw new ServiceException(ResponseCode.FORBIDDEN);
        }

        return principal;
    }

    private AdminPrincipal requireAdminPrincipal(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        if (!ADMIN_ROLES.contains(principal.role())) {
            throw new ServiceException(ResponseCode.FORBIDDEN);
        }

        return principal;
    }

    private void validateAllowedRoles(String[] allowedRoles) {
        if (allowedRoles == null
                || allowedRoles.length == 0
                || Arrays.stream(allowedRoles).anyMatch(role -> !ADMIN_ROLES.contains(role))) {
            throw new IllegalArgumentException("허용할 관리자 역할을 올바르게 지정해야 합니다.");
        }
    }
}
