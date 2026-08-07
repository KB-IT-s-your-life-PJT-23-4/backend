package com.example.project.consultation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Faq {

    private Long id;
    private Long categoryId;
    private String question;
    private String prompt;
    private String answer;
    private boolean showBranchButton;
    private boolean showTaxOfficeButton;
}