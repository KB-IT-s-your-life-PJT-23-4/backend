package com.example.project.consultation.dto.response;

import com.example.project.consultation.domain.Faq;

public record FaqAnswerResponse(
        Long faqId,
        String question,
        String answer
) {
    public static FaqAnswerResponse from(Faq faq) {
        return new FaqAnswerResponse(faq.getId(), faq.getQuestion(), faq.getAnswer());
    }
}