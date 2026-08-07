package com.example.project.admin.faq.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@ApiModel(description = "FAQ 생성 및 수정 응답")
public class AdminFaqResponse {

    @ApiModelProperty(value = "FAQ ID", example = "10")
    private Long faqId;

    @ApiModelProperty(value = "카테고리 ID", example = "1")
    private Long categoryId;

    @ApiModelProperty(value = "FAQ 질문", example = "증여세 신고 기한은 언제인가요?")
    private String question;

    @ApiModelProperty(value = "FAQ 검색 및 AI 처리용 프롬프트", example = "증여세 신고 기한 안내")
    private String prompt;

    @ApiModelProperty(
            value = "FAQ 답변",
            example = "증여일이 속하는 달의 말일부터 3개월 이내에 신고해야 합니다."
    )
    private String answer;

    @ApiModelProperty(value = "영업점 찾기 버튼 표시 여부", example = "false")
    private boolean showBranchButton;

    @ApiModelProperty(value = "세무서 찾기 버튼 표시 여부", example = "true")
    private boolean showTaxOfficeButton;

}
