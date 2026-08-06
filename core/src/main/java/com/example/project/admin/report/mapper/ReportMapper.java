package com.example.project.admin.report.mapper;

import com.example.project.consultation.domain.AiSafetyReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {

    List<AiSafetyReportVO> selectAiReportsPage(
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("status") String status,
            @Param("reportType") String reportType
    );

    long countAiReports(
            @Param("status") String status,
            @Param("reportType") String reportType
    );
}
