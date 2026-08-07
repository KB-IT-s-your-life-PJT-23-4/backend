package com.example.project.admin.auth.domain;

import java.security.Principal;
import java.util.Objects;

public final class AdminPrincipal implements Principal {

    private final Long userId;
    private final String role;

    public AdminPrincipal(Long userId, String role) {
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

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AdminPrincipal that)) {
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
        return "AdminPrincipal[userId=" + userId + ", role=" + role + "]";
    }
}
