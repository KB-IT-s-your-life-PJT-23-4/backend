package com.example.project.consultation.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

@Data
@NoArgsConstructor
public class ConsultClarificationRequest {
    @NotBlank
    @Size(min = 2, max = 500)
    private String question;

    @NotBlank
    private String intent;

    private boolean requiresCalculation;

    @NotNull
    private Map<String, Object> facts;

    @NotNull
    @Size(min = 1)
    private Map<String, Object> answers;
}
