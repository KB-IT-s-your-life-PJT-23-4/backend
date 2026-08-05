package com.example.project.batch.law.dto.raw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 조문. 조문단위 배열을 감싼다. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Articles {

    @JsonProperty("조문단위")
    private List<ArticleUnit> units;
}
