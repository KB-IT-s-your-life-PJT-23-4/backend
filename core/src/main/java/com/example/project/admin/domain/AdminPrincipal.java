package com.example.project.admin.domain;

import java.security.Principal;

public record AdminPrincipal(Long userId, String role) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
