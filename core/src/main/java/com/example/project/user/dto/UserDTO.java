package com.example.project.user.dto;

import com.example.project.user.domain.UserVO;

import java.time.LocalDateTime;

public record UserDTO(
        Long userId,
        String email,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserDTO from(UserVO user) {
        return new UserDTO(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
