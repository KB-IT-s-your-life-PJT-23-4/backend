package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AdminProductUpdateRequest {

    @NotBlank
    @Size(max = 200)
    private String productName;

    @Size(max = 4000)
    private String description;

    @Size(max = 500)
    private String productUrl;

    @NotBlank
    private String salesStatus;
}