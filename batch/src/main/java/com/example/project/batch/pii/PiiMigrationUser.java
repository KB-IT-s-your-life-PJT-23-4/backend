package com.example.project.batch.pii;

import lombok.Data;

@Data
public class PiiMigrationUser {
    private Long userId;
    private String email;
    private String phone;
    private String userName;
    private String emailEncrypted;
    private String emailHmac;
    private String phoneEncrypted;
    private String phoneHmac;
    private String userNameEncrypted;
}
