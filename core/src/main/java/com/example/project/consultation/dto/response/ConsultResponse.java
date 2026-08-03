package com.example.project.consultation.dto.response;

import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.example.project.consultation.dto.fastapi.ChatStatus;
import com.example.project.consultation.dto.fastapi.ClarificationQuestion;

import java.util.List;
import java.util.Map;

public record ConsultResponse(
        String conversationId,
        ChatStatus status,
        String intent,
        boolean requiresCalculation,
        String answer,
        List<ClarificationQuestion> clarificationQuestions,
        Map<String, Object> facts
) {
    public static ConsultResponse from(ChatResponse response) {
        return new ConsultResponse(
                response.conversationId(),
                response.status(),
                response.intent(),
                response.requiresCalculation(),
                response.answer(),
                response.clarificationQuestions(),
                response.facts()
        );
    }
}