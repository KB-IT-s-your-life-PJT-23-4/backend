package com.example.project.recipient.dto.request;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Data
@RequiredArgsConstructor
public class RecipientRequest {

    private String familyName;
    private String relation;
    private LocalDate birthDate;
    private String familyImg;

}
