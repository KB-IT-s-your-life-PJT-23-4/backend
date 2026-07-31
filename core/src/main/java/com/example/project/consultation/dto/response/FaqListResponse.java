package com.example.project.consultation.dto.response;

import com.example.project.consultation.domain.Faq;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public final class FaqListResponse {

    private final String greetingMessage;
    private final String disclaimer;
    private final List<FaqItem> faqList;

    public String greetingMessage() {
        return greetingMessage;
    }

    public String disclaimer() {
        return disclaimer;
    }

    public List<FaqItem> faqList() {
        return faqList;
    }

    @Data
    @RequiredArgsConstructor
    public static final class FaqItem {

        private final Long faqId;
        private final String question;

        public Long faqId() {
            return faqId;
        }

        public String question() {
            return question;
        }
    }

    public static FaqListResponse of(String greetingMessage, String disclaimer, List<Faq> faqs) {
        List<FaqItem> items = faqs.stream()
                .map(f -> new FaqItem(f.getId(), f.getQuestion()))
                .toList();
        return new FaqListResponse(greetingMessage, disclaimer, items);
    }
}
