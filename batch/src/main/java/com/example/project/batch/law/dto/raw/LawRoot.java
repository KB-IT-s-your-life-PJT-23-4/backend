package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 법령 API 응답의 최상위. {@code {"법령": { ... }}}
 *
 * <p>dto/raw 아래 클래스들은 API JSON 모양을 그대로 옮긴 것이라 우리 도메인 모델이 아니다.
 * 필드명이 한글이라 {@link JsonProperty} 로 매핑하고, 쓰지 않는 키는 무시한다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LawRoot {

    @JsonProperty("법령")
    private Law law;
}
