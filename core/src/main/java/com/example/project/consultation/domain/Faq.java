package com.example.project.consultation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
public class Faq {

    private Long id;
    private String question;
    private String answer;
    private Integer displayOrder;
}