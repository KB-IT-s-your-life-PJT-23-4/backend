package com.example.project.admin.report.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportProcessRequest {

    @NotBlank
    @Size(max = 20)
    private String status;

    @Size(max = 2000)
    private String resolutionNote;
}
