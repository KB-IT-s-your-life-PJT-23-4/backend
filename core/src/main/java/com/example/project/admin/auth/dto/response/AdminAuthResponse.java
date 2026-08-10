package com.example.project.admin.auth.dto.response;

import com.example.project.user.domain.UserVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class AdminAuthResponse {

    private final long adminId;
    private final String email;
    private final String name;
    private final String role;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static AdminAuthResponse of(UserVO user){
        return AdminAuthResponse.builder()
                .adminId(user.getUserId())
                .email(user.getEmail())
                .name(user.getUserName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
