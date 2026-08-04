package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 기본정보. 법령 단위 메타. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LawBasicInfo {

    @JsonProperty("법령ID")
    private String lawId;

    @JsonProperty("법령명_한글")
    private String lawName;

    /** 소관부처·법종구분은 {content, ...코드} 객체라 content 만 꺼낸다. */
    @JsonProperty("법종구분")
    private CodeValue lawType;

    @JsonProperty("소관부처")
    private CodeValue ministry;

    @JsonProperty("공포번호")
    private String promulgationNo;

    /** "20251001" yyyyMMdd 문자열. LocalDate 파싱은 processor 에서. */
    @JsonProperty("공포일자")
    private String promulgationDate;

    @JsonProperty("시행일자")
    private String effectiveDate;
}
