package com.example.project.admin.product.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
public class AdminProductDataVersionCreateRequest {

    @NotBlank
    @Size(max = 30)
    private String versionCode;

    @NotNull
    private LocalDate dataDate;
}