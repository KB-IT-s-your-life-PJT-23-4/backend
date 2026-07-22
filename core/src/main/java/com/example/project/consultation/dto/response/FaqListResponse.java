package com.example.project.consultation.dto.response;

import com.example.project.consultation.domain.Faq;

import java.util.List;

public record FaqListResponse(
        String greetingMessage,
        String disclaimer,
        List<FaqItem> faqList
) {
    public record FaqItem(
            Long faqId,
            String question
    ) {}

    public static FaqListResponse of(String greetingMessage, String disclaimer, List<Faq> faqs) {
        List<FaqItem> items = faqs.stream()
                .map(f -> new FaqItem(f.getId(), f.getQuestion()))
                .toList();
        return new FaqListResponse(greetingMessage, disclaimer, items);
    }
}