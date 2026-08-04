package com.example.project.consultation.dto.request;

import java.util.Map;

public record ConsultClarificationRequest(
        String conversationId,
        String question,
        String intent,
        boolean requiresCalculation,
        Map<String, Object> facts,
        Map<String, Object> answers,
        Long productId
) {}