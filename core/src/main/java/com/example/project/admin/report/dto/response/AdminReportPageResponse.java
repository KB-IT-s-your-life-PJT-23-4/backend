package com.example.project.admin.report.dto.response;

import com.example.project.common.api.Pagination;
import com.example.project.consultation.domain.AiSafetyReportVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class AdminReportPageResponse {
    private final List<AiSafetyReportVO> reports;
    private final Pagination pagination;
}
