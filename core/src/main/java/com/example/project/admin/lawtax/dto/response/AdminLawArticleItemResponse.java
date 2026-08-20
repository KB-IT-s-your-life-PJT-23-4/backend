package com.example.project.admin.lawtax.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
@ApiModel(description = "관리자 법령 조문 목록 항목")
public class AdminLawArticleItemResponse {

    @ApiModelProperty(value = "법령 조문 ID", example = "101")
    private final Long lawId;

    @ApiModelProperty(value = "법령ID (국가법령정보 기준, 개정돼도 불변)", example = "001561")
    private final String lawCode;

    @ApiModelProperty(value = "법령명", example = "상속세및증여세법")
    private final String lawName;

    @ApiModelProperty(value = "법종구분", example = "법률")
    private final String lawType;

    @ApiModelProperty(value = "조문/별표 구분", example = "ARTICLE")
    private final String unitType;

    @ApiModelProperty(value = "조번호", example = "제53조")
    private final String articleNo;

    @ApiModelProperty(value = "조문 제목", example = "증여재산 공제")
    private final String title;

    @ApiModelProperty(value = "법령 시행일자", example = "2024-01-01")
    private final LocalDate effectiveDate;

    @ApiModelProperty(value = "조문 시행일자", example = "2024-01-01")
    private final LocalDate articleEffectiveDate;

    @ApiModelProperty(value = "최근 개정일자", example = "2023-12-31")
    private final LocalDate latestRevisionDate;
}
