package com.example.project.admin.faq.dto.response;

import com.example.project.consultation.domain.Faq;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@ApiModel(description = "관리자 FAQ 목록 항목")
public class AdminFaqItemResponse {

    @ApiModelProperty(value = "FAQ ID", example = "10")
    private final Long faqId;

    @ApiModelProperty(value = "카테고리 ID", example = "1")
    private final Long categoryId;

    @ApiModelProperty(value = "카테고리 이름", example = "증여세 신고")
    private final String categoryName;

    @ApiModelProperty(value = "FAQ 질문", example = "증여세 신고 기한은 언제인가요?")
    private final String question;

    @ApiModelProperty(value = "영업점 찾기 버튼 표시 여부", example = "false")
    private final boolean showBranchButton;

    @ApiModelProperty(value = "세무서 찾기 버튼 표시 여부", example = "true")
    private final boolean showTaxOfficeButton;

    @ApiModelProperty(value = "FAQ 생성 일시", example = "2026-08-07T14:30:00")
    private final LocalDateTime createdAt;

    @ApiModelProperty(value = "FAQ 최종 수정 일시", example = "2026-08-07T15:10:00")
    private final LocalDateTime updatedAt;
}
