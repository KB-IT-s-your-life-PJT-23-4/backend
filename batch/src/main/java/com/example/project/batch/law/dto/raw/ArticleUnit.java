package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/**
 * 조문단위. 조(條) 하나.
 *
 * <p>조문여부가 "전문"인 편·장·절 헤더 행이 이 배열에 섞여 오므로 processor 에서 걸러야 한다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleUnit {

    @JsonProperty("조문번호")
    private String articleNo;

    /** 제3조의2 의 "2". 없으면 null. */
    @JsonProperty("조문가지번호")
    private String articleSubNo;

    @JsonProperty("조문키")
    private String articleKey;

    /** 제목 없는 조문이 있어 null 가능. */
    @JsonProperty("조문제목")
    private String title;

    /** "조문" | "전문". "전문"은 편·장·절 헤더라 적재 대상이 아니다. */
    @JsonProperty("조문여부")
    private String articleFlag;

    @JsonProperty("조문시행일자")
    private String effectiveDate;

    /** String | List | 중첩 List 로 온다. 평탄화는 assembler 에서. */
    @JsonProperty("조문내용")
    private JsonNode content;

    /** 항이 없는 조문(본문만 있는 짧은 조)도 있어 null 가능. */
    @JsonProperty("항")
    private List<Paragraph> paragraphs;
}
