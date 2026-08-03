package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ClarificationDataType {

    @JsonProperty("string")
    STRING,

    @JsonProperty("integer")
    INTEGER,

    @JsonProperty("boolean")
    BOOLEAN,

    @JsonProperty("date")
    DATE
}