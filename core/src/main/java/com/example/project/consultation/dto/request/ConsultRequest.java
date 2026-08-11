package com.example.project.consultation.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;


public record ConsultRequest(
        @NotBlank
        @Size(min = 2, max = 500)
        String question
) {}
