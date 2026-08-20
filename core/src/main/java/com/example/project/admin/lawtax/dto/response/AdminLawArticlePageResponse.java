package com.example.project.admin.lawtax.dto.response;

import com.example.project.common.api.Pagination;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
@ApiModel(description = "관리자 법령 조문 페이지 조회 응답")
public class AdminLawArticlePageResponse {

    @ApiModelProperty(value = "현재 페이지의 법령 조문 목록")
    private List<AdminLawArticleItemResponse> articles;

    @ApiModelProperty(value = "페이지네이션 정보")
    private Pagination pagination;
}
