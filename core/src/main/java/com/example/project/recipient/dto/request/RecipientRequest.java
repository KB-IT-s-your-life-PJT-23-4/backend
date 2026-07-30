package com.example.project.recipient.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
public class RecipientRequest {

    private String familyName;
    private String relation;
    private LocalDate birthDate;
    private String familyImg;

}
