package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder
public record EtfProductData(
        String productName,
        String trackingIndex,
        @JsonProperty("annual_return_5y")
        BigDecimal annualReturn5y
) {}