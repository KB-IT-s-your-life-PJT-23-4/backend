package com.example.project.user.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long userId;
    private String email;
    private String password;
    private String userName;
    private LocalDate birthDate;
    private String phone;
    private String role;
    private String accountStatus;
    private LocalDateTime blockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String img;

    public UserVO(
            Long userId,
            String email,
            String password,
            String userName,
            LocalDate birthDate,
            String phone,
            String role,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String img
    ) {
        this(
                userId,
                email,
                password,
                userName,
                birthDate,
                phone,
                role,
                "ACTIVE",
                null,
                createdAt,
                updatedAt,
                img
        );
    }

    public UserVO(
            Long userId,
            String email,
            String password,
            String userName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.userName = userName;
        this.role = "USER";
        this.accountStatus = "ACTIVE";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
