package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 별표단위. 별표 하나.
 *
 * <p>별표구분이 "서식"인 것은 신고서 빈 양식이라 적재 대상이 아니다.
 * "별표"인 것만 processor 에서 통과시킨다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppendixUnit {

    @JsonProperty("별표번호")
    private String appendixNo;

    @JsonProperty("별표가지번호")
    private String appendixSubNo;

    @JsonProperty("별표키")
    private String appendixKey;

    @JsonProperty("별표제목")
    private String title;

    /** "별표" | "서식". "서식"은 제외 대상. */
    @JsonProperty("별표구분")
    private String appendixType;

    @JsonProperty("별표내용")
    private JsonNode content;
}
