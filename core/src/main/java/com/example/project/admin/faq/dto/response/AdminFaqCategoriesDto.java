package com.example.project.admin.faq.dto.response;

import com.example.project.consultation.domain.FaqCategory;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
@ApiModel(description = "관리자 FAQ 카테고리 목록 응답")
public class AdminFaqCategoriesDto {

    @ApiModelProperty(value = "FAQ 카테고리 목록")
    List<FaqCategory> categories;
}
