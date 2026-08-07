package com.example.project.admin.user.service;

import com.example.project.admin.user.domain.AdminUserRecord;
import com.example.project.admin.user.dto.response.AdminUserPageResponse;
import com.example.project.admin.user.dto.response.AdminUserResponse;
import com.example.project.admin.user.mapper.AdminUserMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserMapper adminUserMapper;
    private final UserService userService;

    @Transactional(readOnly = true)
    public AdminUserPageResponse getUsers(
            Long userId,
            String emailValue,
            String nameValue,
            int page,
            int size
    ) {
        validatePagination(page, size);
        if (userId != null && userId <= 0L) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String email = normalize(emailValue, true);
        String name = normalize(nameValue, false);
        long totalElements = adminUserMapper.countUsers(userId, email, name);
        long offset = (long) page * size;
        List<AdminUserRecord> rows = adminUserMapper.selectUsers(
                userId,
                email,
                name,
                offset,
                size
        );
        List<AdminUserResponse> users = rows == null
                ? List.of()
                : rows.stream().map(AdminUserResponse::from).toList();

        int totalPages = totalPages(totalElements, size);
        boolean hasNext = page + 1 < totalPages;
        Pagination pagination = Pagination.builder()
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .numberOfElements(users.size())
                .first(page == 0)
                .last(!hasNext)
                .hasNext(hasNext)
                .hasPrevious(page > 0)
                .build();
        return new AdminUserPageResponse(users, pagination);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        AdminUserRecord user = adminUserMapper.selectUserById(userId);
        if (user == null) {
            throw new ServiceException(ResponseCode.MEMBER_NOT_FOUND);
        }
        return AdminUserResponse.from(user);
    }

    public void deleteUser(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        userService.deleteUser(userId);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
    }

    private String normalize(String value, boolean lowercase) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return lowercase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private int totalPages(long totalElements, int size) {
        long pages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }
}
