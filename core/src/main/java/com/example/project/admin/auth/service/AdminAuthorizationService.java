package com.example.project.admin.auth.service;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.auth.dto.request.AdminAuthCreateRequest;
import com.example.project.admin.auth.dto.request.AdminChangeAuthRequest;
import com.example.project.admin.auth.dto.response.AdminAuthPageResponse;
import com.example.project.admin.auth.dto.response.AdminAuthResponse;
import com.example.project.admin.auth.dto.response.AdminMeResponse;
import com.example.project.admin.auth.mapper.AdminAuthMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.domain.UserVO;
import com.example.project.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private static final Set<String> ADMIN_ROLES = Set.of("ROOT", "MIDDLE", "DEFAULT");

    private final UserMapper userMapper;
    private final AdminAuthMapper adminAuthMapper;
    private final PasswordEncoder passwordEncoder;

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

    @Transactional(readOnly = true)
    public AdminAuthPageResponse getAdmins(
            int page,
            int size
    ) {

        long offset = Pagination.calculateOffset(page, size);

        long totalAdmins = adminAuthMapper.getAdminCounts(ADMIN_ROLES);

        List<UserVO> admins = adminAuthMapper.getAdmins(
                ADMIN_ROLES,
                offset,
                size
        );

        List<AdminAuthResponse> items = admins.stream()
                .filter(admin -> admin != null && isAdminRole(admin.getRole()))
                .map(AdminAuthResponse::of)
                .toList();

        Pagination pagination = Pagination.of(
                page,
                size,
                totalAdmins,
                items.size()
        );

        return AdminAuthPageResponse.builder()
                .admins(items)
                .pagination(pagination)
                .build();
    }

    @Transactional
    public void changeAuth(Long userId, AdminChangeAuthRequest request) {
        validateUserId(userId);
        if (request == null || request.getRole() == null || request.getRole().isBlank()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        requireUser(userId);
        String role = request.getRole().trim().toUpperCase(Locale.ROOT);
        if (!ADMIN_ROLES.contains(role)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        validateAffectedRows(adminAuthMapper.changeAuth(userId, role));
    }

    @Transactional
    public void deleteAdmin(Long userId) {
        validateUserId(userId);
        UserVO user = requireUser(userId);
        if (!isAdminRole(user.getRole())) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }

        validateAffectedRows(adminAuthMapper.deleteAdmin(userId));
    }

    private UserVO requireUser(Long userId) {
        return adminAuthMapper.findByUserId(userId)
                .orElseThrow(() -> new ServiceException(ResponseCode.MEMBER_NOT_FOUND));
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
    }

    private void validateAffectedRows(int affectedRows) {
        if (affectedRows == 0) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        if (affectedRows != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }

    @Transactional
    public AdminAuthResponse createAdmin(AdminAuthCreateRequest request) {
        if (request == null) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String email = normalizeRequired(request.getEmail()).toLowerCase(Locale.ROOT);
        String name = normalizeRequired(request.getName());
        String phone = normalizeRequired(request.getPhone());
        String role = normalizeRequired(request.getRole()).toUpperCase(Locale.ROOT);
        String password = request.getPassword();

        if (password == null || password.isBlank() || !ADMIN_ROLES.contains(role)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        if (userMapper.findByEmail(email) != null) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        UserVO admin = new UserVO();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setUserName(name);
        admin.setPhone(phone);
        admin.setRole(role);

        try {
            if (adminAuthMapper.createAdmin(admin) != 1) {
                throw new ServiceException(ResponseCode.DATABASE_ERROR);
            }
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        UserVO createdAdmin = adminAuthMapper.findByUserId(admin.getUserId())
                .orElseThrow(() -> new ServiceException(ResponseCode.DATABASE_ERROR));

        return AdminAuthResponse.of(createdAdmin);
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        return value.trim();
    }
}
