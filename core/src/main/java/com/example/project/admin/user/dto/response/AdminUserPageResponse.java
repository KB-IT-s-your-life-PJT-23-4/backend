package com.example.project.admin.user.dto.response;

import com.example.project.common.api.Pagination;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminUserPageResponse {

    private final List<AdminUserResponse> users;
    private final Pagination pagination;
}
