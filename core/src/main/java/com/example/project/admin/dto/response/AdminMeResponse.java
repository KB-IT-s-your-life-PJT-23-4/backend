package com.example.project.admin.dto.response;

import com.example.project.admin.domain.AdminPrincipal;

import java.util.Objects;

public final class AdminMeResponse {

    private final Long userId;
    private final String role;

    public AdminMeResponse(Long userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public Long userId() {
        return userId;
    }

    public Long getUserId() {
        return userId;
    }

    public String role() {
        return role;
    }

    public String getRole() {
        return role;
    }

    public static AdminMeResponse from(AdminPrincipal principal) {
        return new AdminMeResponse(principal.userId(), principal.role());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AdminMeResponse that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }

    @Override
    public String toString() {
        return "AdminMeResponse[userId=" + userId + ", role=" + role + "]";
    }
}
