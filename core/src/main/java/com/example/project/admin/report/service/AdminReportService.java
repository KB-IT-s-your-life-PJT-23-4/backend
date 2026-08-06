package com.example.project.admin.report.service;

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

    private static final int MAX_PAGE_SIZE = 100;

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
        validatePagination(page, size);

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

        long offset = (long) page * size;

        List<AiSafetyReportVO> items = reportMapper.selectAiReportsPage(
                status,
                reportType,
                offset,
                size
        );

        int totalPages = calculateTotalPages(totalElements, size);

        boolean hasNext = page + 1 < totalPages;

        Pagination pagination = Pagination.builder()
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .size(items.size())
                .first(page == 0)
                .last(!hasNext)
                .hasNext(hasNext)
                .hasPrevious(page > 0)
                .build();

        return AdminReportPageResponse.builder()
                .reports(items)
                .pagination(pagination)
                .build();
    }
    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ServiceException(
                    ResponseCode.BAD_REQUEST
            );
        }
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

    private int calculateTotalPages(
            long totalElements,
            int size
    ) {
        long pages =
                totalElements / size
                        + (totalElements % size == 0 ? 0 : 1);

        return pages > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) pages;
    }
}
