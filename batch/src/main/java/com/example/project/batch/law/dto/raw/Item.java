package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/** 호(號). 항 아래 단위. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {

    /** "1.", "2." … 번호가 내용 앞에 이미 포함돼 있어 조립 시 따로 붙이지 않는다. */
    @JsonProperty("호번호")
    private String itemNo;

    @JsonProperty("호내용")
    private JsonNode content;

    @JsonProperty("목")
    private List<SubItem> subItems;
}
