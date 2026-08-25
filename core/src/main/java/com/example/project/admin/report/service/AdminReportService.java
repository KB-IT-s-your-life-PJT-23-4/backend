package com.example.project.admin.report.service;

import com.example.project.admin.audit.service.AdminAuditWriter;
import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.report.domain.AdminReportQuestionRow;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final AdminAuditWriter adminAuditWriter;

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
        populateQuestionExcerpts(items);

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

    private void populateQuestionExcerpts(List<AiSafetyReportVO> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }

        List<Long> reportIds = reports.stream()
                .map(AiSafetyReportVO::getAiSafetyReportId)
                .toList();
        List<AdminReportQuestionRow> questionRows =
                reportMapper.selectReportQuestionExcerpts(reportIds);

        Map<Long, List<String>> excerptsByReportId = new HashMap<>();
        for (AdminReportQuestionRow row : questionRows) {
            if (row.getQuestionExcerpt() == null) {
                continue;
            }
            excerptsByReportId
                    .computeIfAbsent(row.getReportId(), ignored -> new ArrayList<>())
                    .add(row.getQuestionExcerpt());
        }

        for (AiSafetyReportVO report : reports) {
            report.setQuestionExcerpts(List.copyOf(
                    excerptsByReportId.getOrDefault(
                            report.getAiSafetyReportId(),
                            List.of()
                    )
            ));
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

    @Transactional
    public void processReport(
            long reportId,
            ReportProcessRequest request,
            AdminPrincipal actor
    ) {
        if (reportId <= 0
                || actor == null
                || actor.userId() == null
                || actor.userId() <= 0
                || request == null) {
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
                actor.userId(),
                resolutionNote
        );

        if (updatedRows == 0) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        if (updatedRows != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        adminAuditWriter.record(
                actor,
                "REPORT_PROCESS",
                "SAFETY_REPORT",
                reportId,
                "AI 안전 신고를 처리했습니다.",
                Map.of(
                        "status", status,
                        "resolutionNoteChanged", resolutionNote != null
                )
        );
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
