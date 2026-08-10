package com.example.project.consultation.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KakaoKeywordSearchResponse(
        List<Document> documents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Document(
            String placeName,
            String addressName,
            String roadAddressName,
            String phone,
            String x,
            String y,
            String placeUrl,
            String categoryGroupCode
    ) {}
}