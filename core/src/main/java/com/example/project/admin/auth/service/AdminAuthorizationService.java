package com.example.project.admin.auth.service;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.auth.dto.response.AdminMeResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private static final Set<String> ADMIN_ROLES = Set.of("ROOT", "MIDDLE", "DEFAULT");

    private final UserMapper userMapper;

    public Optional<AdminPrincipal> findCurrentUser(Object authenticatedPrincipal) {
        Long authenticatedUserId = parseUserId(authenticatedPrincipal);
        if (authenticatedUserId == null) {
            return Optional.empty();
        }

        UserVO user = userMapper.findById(authenticatedUserId);
        if (user == null || !authenticatedUserId.equals(user.getUserId())) {
            return Optional.empty();
        }

        return Optional.of(new AdminPrincipal(authenticatedUserId, user.getRole()));
    }

    public boolean isAdminRole(String role) {
        return role != null && ADMIN_ROLES.contains(role);
    }

    public AdminMeResponse getCurrentAdmin(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        if (!isAdminRole(principal.role())) {
            throw new ServiceException(ResponseCode.FORBIDDEN);
        }

        return AdminMeResponse.from(principal);
    }

    private Long parseUserId(Object principal) {
        if (principal == null) {
            return null;
        }

        String value = principal instanceof Principal javaPrincipal
                ? javaPrincipal.getName()
                : principal.toString();

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
