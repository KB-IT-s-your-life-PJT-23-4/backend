package com.example.project.admin.report.service;

import com.example.project.admin.report.dto.request.ReportProcessRequest;
import com.example.project.admin.report.dto.response.AdminReportPageResponse;
import com.example.project.admin.report.mapper.ReportMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.AiSafetyReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.Locale;


@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final Set<String> REPORT_STATUSES =
            Set.of(
                    "OPEN",
                    "IN_REVIEW",
                    "RESOLVED",
                    "DISMISSED"
            );

    private static final Set<String> REPORT_TYPES =
            Set.of(
                    "JAILBREAK",
                    "OTHER_THRESHOLD"
            );

    private final ReportMapper reportMapper;

    @Transactional(readOnly = true)
    public AdminReportPageResponse getPageReportList(
            int page,
            int size,
            String statusValue,
            String reportTypeValue
    ) {
        long offset = Pagination.calculateOffset(page, size);

        String status = normalizeFilter(
                statusValue,
                REPORT_STATUSES
        );

        String reportType = normalizeFilter(
                reportTypeValue,
                REPORT_TYPES
        );

        long totalElements = reportMapper.countAiReports(
                status,
                reportType
        );

        List<AiSafetyReportVO> items = reportMapper.selectAiReportsPage(
                status,
                reportType,
                offset,
                size
        );

        Pagination pagination = Pagination.of(
                page,
                size,
                totalElements,
                items.size()
        );

        return AdminReportPageResponse.builder()
                .reports(items)
                .pagination(pagination)
                .build();
    }
    private String normalizeFilter(
            String value,
            Set<String> allowedValues
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.trim().toUpperCase(Locale.ROOT);

        if (!allowedValues.contains(normalized)) {
            throw new ServiceException(
                    ResponseCode.BAD_REQUEST
            );
        }

        return normalized;
    }

    @Transactional
    public void processReport(long reportId, ReportProcessRequest request, long adminId) {
        if (reportId <= 0 || adminId <= 0 || request == null) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String status = normalizeFilter(request.getStatus(), REPORT_STATUSES);
        if (status == null) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String resolutionNote = normalizeResolutionNote(request.getResolutionNote());
        int updatedRows = reportMapper.updateReport(
                reportId,
                status,
                adminId,
                resolutionNote
        );

        if (updatedRows == 0) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        if (updatedRows != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }

    private String normalizeResolutionNote(String resolutionNote) {
        if (resolutionNote == null || resolutionNote.isBlank()) {
            return null;
        }
        if (resolutionNote.length() > 2000) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        return resolutionNote.trim();
    }
}
