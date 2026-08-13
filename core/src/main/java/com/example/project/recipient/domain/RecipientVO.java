package com.example.project.recipient.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RecipientVO {

    private Long familyId;
    private Long userId;
    private String familyName;
    private String relation;
    private LocalDate birthDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String familyImg;
    private String familyNameEncrypted;
    private String birthDateEncrypted;

}
