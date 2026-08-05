package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** 목(目). 가장 아래 단위. 더 내려가는 하위 구조는 없다. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubItem {

    /** "가.", "나." … 번호가 내용에 이미 포함돼 있다. */
    @JsonProperty("목번호")
    private String subItemNo;

    @JsonProperty("목내용")
    private JsonNode content;
}
