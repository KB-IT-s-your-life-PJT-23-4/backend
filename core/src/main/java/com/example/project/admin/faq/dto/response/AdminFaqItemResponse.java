package com.example.project.admin.faq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class AdminFaqItemResponse {

    private final Long faqId;
    private final Long categoryId;
    private final String categoryName;
    private final String question;
    private final boolean showBranchButton;
    private final boolean showTaxOfficeButton;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
