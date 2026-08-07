package com.example.project.admin.faq.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
@ApiModel(description = "FAQ 카테고리 생성 및 수정 응답")
public class AdminCategoryResponse {

    @ApiModelProperty(value = "카테고리 ID", example = "1")
    private final long categoryId;

    @ApiModelProperty(value = "카테고리 이름", example = "증여세 신고")
    private final String categoryName;
}
