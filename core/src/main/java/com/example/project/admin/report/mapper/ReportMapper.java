package com.example.project.admin.report.mapper;

import com.example.project.consultation.domain.AiSafetyReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {

    List<AiSafetyReportVO> selectAiReportsPage(
            @Param("status") String status,
            @Param("reportType") String reportType,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAiReports(
           @Param("status") String status,
           @Param("reportType") String reportType);

    int updateReport(
            @Param("reportId") long reportId,
            @Param("status") String status,
            @Param("adminId") long adminId,
            @Param("resolutionNote") String resolutionNote);
}
