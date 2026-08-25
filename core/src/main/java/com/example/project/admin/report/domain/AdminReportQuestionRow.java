package com.example.project.admin.report.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportQuestionRow {

    private Long reportId;
    private String questionExcerpt;
}
