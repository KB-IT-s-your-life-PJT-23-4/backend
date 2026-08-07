package com.example.project.admin.faq.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@NoArgsConstructor
public class AdminCategoryRequest {

    private Long categoryId;

    @NotBlank
    @Size(max = 200)
    private String categoryName;
}
