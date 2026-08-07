package com.example.project.admin.faq.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@NoArgsConstructor
@ApiModel(description = "FAQ 카테고리 생성 및 수정 요청")
public class AdminCategoryRequest {

    @ApiModelProperty(
            value = "카테고리 ID. 생성 요청에서는 생략하고 수정 요청에서는 필수입니다.",
            example = "1"
    )
    private Long categoryId;

    @NotBlank
    @Size(max = 200)
    @ApiModelProperty(
            value = "카테고리 이름",
            required = true,
            example = "증여세 신고"
    )
    private String categoryName;
}
