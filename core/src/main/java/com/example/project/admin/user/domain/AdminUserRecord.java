package com.example.project.admin.user.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AdminUserRecord {

    private Long userId;
    private String email;
    private String userName;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long recipientCount;
    private Long giftCount;
    private Long simulationCount;
}
