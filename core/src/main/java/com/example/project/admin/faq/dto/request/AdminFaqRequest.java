package com.example.project.admin.faq.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "FAQ 생성 및 수정 요청")
public class AdminFaqRequest {

    @NotNull
    @Positive
    @ApiModelProperty(
            value = "FAQ가 속할 카테고리 ID",
            required = true,
            example = "1"
    )
    private Long categoryId;

    @NotBlank
    @Size(max = 255)
    @ApiModelProperty(
            value = "화면에 표시할 FAQ 질문",
            required = true,
            example = "증여세 신고 기한은 언제인가요?"
    )
    private String question;

    @NotBlank
    @Size(max = 500)
    @ApiModelProperty(
            value = "FAQ 검색 및 AI 처리에 사용하는 프롬프트",
            required = true,
            example = "증여세 신고 기한 안내"
    )
    private String prompt;

    @NotBlank
    @ApiModelProperty(
            value = "FAQ 답변",
            required = true,
            example = "증여일이 속하는 달의 말일부터 3개월 이내에 신고해야 합니다."
    )
    private String answer;

    @ApiModelProperty(
            value = "영업점 찾기 버튼 표시 여부",
            example = "false"
    )
    private boolean showBranchButton;

    @ApiModelProperty(
            value = "세무서 찾기 버튼 표시 여부",
            example = "true"
    )
    private boolean showTaxOfficeButton;
}
