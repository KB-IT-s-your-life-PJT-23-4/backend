package com.example.project.admin.report.controller;

import com.example.project.admin.report.dto.response.AdminReportPageResponse;
import com.example.project.admin.report.service.AdminReportService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.consultation.domain.AiSafetyReportVO;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

@ApiLog
@Api(tags = "관리자 신고 관리 API")
@RestController
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
}
