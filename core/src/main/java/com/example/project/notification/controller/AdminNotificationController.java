package com.example.project.notification.controller;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.notification.dto.response.AdminNotificationPageResponse;
import com.example.project.notification.service.AdminNotificationService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;

@ApiLog
@Api(tags = "관리자 알림 API")
@RestController
@Validated
@RequestMapping("/api/admin/notification")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @GetMapping
    public ApiResponse<AdminNotificationPageResponse> getPageNotificationList(
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(Pagination.MAX_PAGE_SIZE)
            int size,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String notificationType,

            @RequestParam(defaultValue = "false")
            boolean unreadOnly,

            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        AdminNotificationPageResponse response = adminNotificationService.getPageNotificationList(
                page,
                size,
                status,
                notificationType,
                unreadOnly,
                principal.userId()
        );

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    /** 헤더 배지 전용. 목록을 통째로 받지 않고 숫자만 폴링할 수 있게 따로 둔다. */
    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        long unreadCount = adminNotificationService.countUnread(principal.userId());

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), unreadCount);
    }

    @PatchMapping("/{adminNotificationId}/read")
    public ApiResponse<Void> markRead(
            @Positive @PathVariable long adminNotificationId,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        adminNotificationService.markRead(adminNotificationId, principal.userId());

        return ApiResponse.success(ResponseCode.UPDATED, httpRequest.getRequestURI(), null);
    }

    /** 읽음과 달리 알림 자체의 상태를 바꾼다. 한 명이 처리하면 모든 관리자에게 처리 완료로 보인다. */
    @PatchMapping("/{adminNotificationId}/resolve")
    public ApiResponse<Void> resolve(
            @Positive @PathVariable long adminNotificationId,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        adminNotificationService.resolve(adminNotificationId, principal.userId());

        return ApiResponse.success(ResponseCode.UPDATED, httpRequest.getRequestURI(), null);
    }
}
