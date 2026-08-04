package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 법령 본문. 기본정보(메타) + 조문 + 별표. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Law {

    /** 법령키 = 법령ID+시행일자+공포번호. 기본정보가 아니라 법령 바로 아래에 있다. */
    @JsonProperty("법령키")
    private String lawKey;

    @JsonProperty("기본정보")
    private LawBasicInfo basicInfo;

    @JsonProperty("조문")
    private Articles articles;

    /** 별표는 시행령·시행규칙에만 있고 법률에는 없다. 없으면 null. */
    @JsonProperty("별표")
    private Appendices appendices;
}
