package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ClarificationRequest(
        String conversationId,
        String question,
        String intent,
        boolean requiresCalculation,
        Map<String, Object> facts,
        Map<String, Object> answers,
        List<FamilyData> families,
        Object product
) {}
