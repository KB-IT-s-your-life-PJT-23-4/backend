package com.example.project.consultation.dto.response;

import com.example.project.consultation.domain.Faq;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public final class FaqAnswerResponse {

    private final Long faqId;
    private final String question;
    private final String answer;

    public Long faqId() {
        return faqId;
    }

    public String question() {
        return question;
    }

    public String answer() {
        return answer;
    }

    public static FaqAnswerResponse from(Faq faq) {
        return new FaqAnswerResponse(faq.getId(), faq.getQuestion(), faq.getAnswer());
    }
}
