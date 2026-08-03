package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatResponse(
        String conversationId,
        ChatStatus status,
        String intent,
        boolean requiresCalculation,
        String answer,
        List<ClarificationQuestion> clarificationQuestions,
        Map<String, Object> facts
) {}