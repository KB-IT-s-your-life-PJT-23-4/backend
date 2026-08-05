package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 별표. 별표단위 배열을 감싼다. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Appendices {

    @JsonProperty("별표단위")
    private List<AppendixUnit> units;
}
