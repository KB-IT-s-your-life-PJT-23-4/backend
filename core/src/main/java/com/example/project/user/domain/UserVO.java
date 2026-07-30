package com.example.project.user.domain;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long userId;
    private String email;
    private String password;
    private String userName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
