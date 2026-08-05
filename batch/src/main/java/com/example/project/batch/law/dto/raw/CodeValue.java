package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * {@code {"content": "법률", "법종구분코드": "A0002"}} 처럼 값과 코드가 함께 오는 객체.
 * 우리는 content 만 쓴다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeValue {

    @JsonProperty("content")
    private String content;
}
