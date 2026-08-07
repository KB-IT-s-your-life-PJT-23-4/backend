package com.example.project.admin.faq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class AdminCategoryResponse {
    private final long categoryId;
    private final String categoryName;
}
