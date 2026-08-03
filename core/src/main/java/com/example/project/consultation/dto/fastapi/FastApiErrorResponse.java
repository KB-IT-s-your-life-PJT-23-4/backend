package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FastApiErrorResponse(
        boolean success,
        int statusCode,
        String timestamp,
        String path,
        ErrorDetail error
) {
    public record ErrorDetail(
            String code,
            String message,
            List<Object> details
    ) {}
}