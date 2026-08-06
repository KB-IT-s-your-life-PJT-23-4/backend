package com.example.project.admin.report.mapper;

import com.example.project.consultation.domain.AiSafetyReportVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReportMapper {

    List<AiSafetyReportVO> selectAiReportsPage(String status, String reportType, long offset, int page);

    int countAiReports(String status, String reportType);
}
