package com.example.project.admin.dto.response;

import com.example.project.admin.domain.AdminPrincipal;

public record AdminMeResponse(Long userId, String role) {

    public static AdminMeResponse from(AdminPrincipal principal) {
        return new AdminMeResponse(principal.userId(), principal.role());
    }
}
