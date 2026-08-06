package com.example.project.admin.report.service;

import com.example.project.admin.report.dto.response.AdminReportPageResponse;
import com.example.project.admin.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

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
    public AdminReportPageResponse getPageReportList()
}
