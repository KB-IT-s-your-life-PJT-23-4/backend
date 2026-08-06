package com.example.project.admin.report.service;

import com.example.project.admin.report.dto.response.AdminReportPageResponse;
import com.example.project.admin.report.mapper.ReportMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.AiSafetyReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> REPORT_STATUSES = Set.of(
            "OPEN",
            "IN_REVIEW",
            "RESOLVED",
            "DISMISSED"
    );
    private static final Set<String> REPORT_TYPES = Set.of(
            "JAILBREAK",
            "OTHER_THRESHOLD"
    );

    private final ReportMapper reportMapper;

    @Transactional(readOnly = true)
    public AdminReportPageResponse getPageReportList(
            int page,
            int size,
            String status,
            String reportType
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String normalizedStatus = normalize(status);
        String normalizedReportType = normalize(reportType);
        validateFilter(normalizedStatus, REPORT_STATUSES);
        validateFilter(normalizedReportType, REPORT_TYPES);

        long offset = (long) page * size;
        List<AiSafetyReportVO> reports = reportMapper.selectAiReportsPage(
                offset,
                size,
                normalizedStatus,
                normalizedReportType
        );
        long totalElements = reportMapper.countAiReports(
                normalizedStatus,
                normalizedReportType
        );
        int totalPages = totalElements == 0L
                ? 0
                : (int) Math.min(
                        Integer.MAX_VALUE,
                        (totalElements + size - 1L) / size
                );
        boolean hasPrevious = page > 0;
        boolean hasNext = page + 1L < totalPages;

        AdminReportPageResponse.Pagination pagination =
                new AdminReportPageResponse.Pagination(
                        page,
                        size,
                        totalElements,
                        totalPages,
                        reports.size(),
                        page == 0,
                        !hasNext,
                        hasNext,
                        hasPrevious
                );

        return new AdminReportPageResponse(reports, pagination);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateFilter(String value, Set<String> allowedValues) {
        if (value != null && !allowedValues.contains(value)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
    }
}
