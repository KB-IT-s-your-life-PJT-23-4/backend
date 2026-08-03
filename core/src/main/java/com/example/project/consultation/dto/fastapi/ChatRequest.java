package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatRequest(
        String conversationId,
        String question,
        List<FamilyData> families,
        Object product,
        Map<String, Object> facts
) {}
