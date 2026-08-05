package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/**
 * 항(項). 조문 아래 단위.
 *
 * <p>항번호 없이 호만 가진 항이 있다(예: 제2조 정의). 그때 항내용은 null 이고 호로 바로 내려간다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Paragraph {

    /** "①", "②" … 없을 수 있다. */
    @JsonProperty("항번호")
    private String paragraphNo;

    /** String | List | 중첩 List. 없으면 null. */
    @JsonProperty("항내용")
    private JsonNode content;

    @JsonProperty("호")
    private List<Item> items;

    /**
     * 목이 호 아래가 아니라 항 레벨에 평평하게 실려 오는 경우가 있다(예: 제2조 정의).
     * 어느 호에 속하는지는 표시되지 않아 assembler 가 목번호로 그룹을 잘라 배분한다.
     */
    @JsonProperty("목")
    private List<SubItem> subItems;
}
