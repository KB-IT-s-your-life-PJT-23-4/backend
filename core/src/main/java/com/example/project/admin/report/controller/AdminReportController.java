package com.example.project.admin.report.controller;

import com.example.project.admin.report.dto.response.AdminReportPageResponse;
import com.example.project.admin.report.service.AdminReportService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@ApiLog
@Api(tags = "Admin report API")
@Validated
@RestController
@RequestMapping("/api/admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    public ApiResponse<AdminReportPageResponse> getPageReportList(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reportType,
            HttpServletRequest httpRequest
    ) {
        AdminReportPageResponse response = adminReportService.getPageReportList(
                page,
                size,
                status,
                reportType
        );

        return ApiResponse.success(
                ResponseCode.SUCCESS,
                httpRequest.getRequestURI(),
                response
        );
    }
}
