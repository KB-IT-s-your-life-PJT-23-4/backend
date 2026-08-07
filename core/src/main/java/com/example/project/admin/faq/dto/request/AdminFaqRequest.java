package com.example.project.admin.faq.dto.request;

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
public class AdminFaqRequest {

    @NotNull
    @Positive
    private Long categoryId;

    @NotBlank
    @Size(max = 255)
    private String question;

    @NotBlank
    @Size(max = 500)
    private String prompt;

    @NotBlank
    private String answer;

    private boolean showBranchButton;
    private boolean showTaxOfficeButton;
}
