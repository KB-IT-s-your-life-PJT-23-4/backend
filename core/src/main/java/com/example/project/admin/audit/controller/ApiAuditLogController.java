package com.example.project.admin.audit.controller;

import com.example.project.admin.audit.dto.response.AdminAuditLogPageResponse;
import com.example.project.admin.audit.service.AdminAuditService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.security.AdminAccessValidator;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;


@ApiLog
@Api(tags = "관리자 감사 로그 API")
@Validated
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class ApiAuditLogController {

    private final AdminAuditService adminAuditService;
    private final AdminAccessValidator adminAccessValidator;

    @GetMapping
    public ApiResponse<AdminAuditLogPageResponse> getAuditLogs(
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(Pagination.MAX_PAGE_SIZE)
            int size,

            @RequestParam(required = false)
            Long actorUserId,

            @RequestParam(required = false)
            String actionType,

            @RequestParam(required = false)
            String targetType,

            @RequestParam(required = false)
            String targetId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,

            Authentication authentication,
            HttpServletRequest request
    ) {
        adminAccessValidator.requireRoot(authentication);

        AdminAuditLogPageResponse data = adminAuditService.getAuditLogs(
                page, size, actorUserId, actionType, targetType, targetId, from, to);

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }


}
