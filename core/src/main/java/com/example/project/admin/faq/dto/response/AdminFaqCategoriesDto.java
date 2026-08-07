package com.example.project.admin.faq.dto.response;

import com.example.project.consultation.domain.FaqCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class AdminFaqCategoriesDto {
    List<FaqCategory> categories;
}
