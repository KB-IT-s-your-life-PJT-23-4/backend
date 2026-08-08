package com.example.project.admin.report.controller;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.report.dto.request.ReportProcessRequest;
import com.example.project.admin.report.dto.response.AdminReportPageResponse;
import com.example.project.admin.report.service.AdminReportService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;

@ApiLog
@Api(tags = "관리자 신고 관리 API")
@RestController
@Validated
@RequestMapping("/api/admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    public ApiResponse<AdminReportPageResponse> getPageReportList(
            @RequestParam(defaultValue = "0")
            @Min(0)
            Integer page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(Pagination.MAX_PAGE_SIZE)
            int size,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String reportType,

            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ){
        AdminReportPageResponse response = adminReportService.getPageReportList(
                page,
                size,
                status,
                reportType
        );

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @PatchMapping("/{reportId}")
    public ApiResponse<Void> processReport(
            @Valid @RequestBody ReportProcessRequest request,
            @Positive @PathVariable long reportId,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        adminReportService.processReport(reportId, request, principal.userId());

        return ApiResponse.success(
                ResponseCode.UPDATED,
                httpRequest.getRequestURI(),
                null
        );
    }
}
