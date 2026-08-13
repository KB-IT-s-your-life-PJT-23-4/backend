package com.example.project.batch.pii;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PiiMigrationFamily {
    private Long familyId;
    private String familyName;
    private LocalDate birthDate;
    private String familyNameEncrypted;
    private String birthDateEncrypted;
}
