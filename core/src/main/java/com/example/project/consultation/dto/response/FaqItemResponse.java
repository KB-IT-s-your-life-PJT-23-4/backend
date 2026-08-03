package com.example.project.consultation.dto.response;

import com.example.project.consultation.domain.Faq;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FaqItemResponse {

    private final Long faqId;
    private final String question;
    private final String prompt;
    private final String answer;
    private final boolean showBranchButton;
    private final boolean showTaxOfficeButton;

    public static FaqItemResponse from(Faq faq) {
        return new FaqItemResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getPrompt(),
                faq.getAnswer(),
                faq.isShowBranchButton(),
                faq.isShowTaxOfficeButton()
        );
    }
}