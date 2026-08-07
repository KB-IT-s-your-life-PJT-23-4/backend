package com.example.project.admin.faq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminFaqResponse {
    private Long faqId;
    private Long categoryId;
    private String question;
    private String prompt;
    private String answer;
    private boolean showBranchButton;
    private boolean showTaxOfficeButton;

}
