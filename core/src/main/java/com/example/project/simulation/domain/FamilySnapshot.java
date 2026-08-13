package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;

@Data
public class FamilySnapshot {
    private Long familyId;
    private Long userId;
    private String familyName;
    private String relation;
    private LocalDate birthDate;
    private String familyNameEncrypted;
    private String birthDateEncrypted;
}
