package com.example.project.admin.auth.controller;

import com.example.project.admin.auth.dto.request.AdminChangeAuthRequest;
import com.example.project.admin.auth.dto.response.AdminAuthPageResponse;
import com.example.project.admin.auth.dto.response.AdminMeResponse;
import com.example.project.admin.auth.service.AdminAuthorizationService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.security.AdminAccessValidator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;

@Api(tags = "관리자 인증 API", description = "현재 관리자 인증 및 권한 확인")
@ApiLog
@RestController
@Validated
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminAccessValidator adminAccessValidator;

    @ApiOperation(
            value = "현재 관리자 조회",
            notes = "JWT로 인증된 사용자의 현재 DB 역할을 기준으로 관리자 식별 정보를 반환합니다."
    )
    @GetMapping("/me")
    public ApiResponse<AdminMeResponse> me(
            Authentication authentication,
            HttpServletRequest request
    ) {
        AdminMeResponse data = adminAuthorizationService.getCurrentAdmin(authentication);
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping("/auth")
    public ApiResponse<AdminAuthPageResponse> getAdmins(
            @RequestParam(defaultValue = "0")
            @Min(0)
            Integer page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(Pagination.MAX_PAGE_SIZE)
            int size,

            Authentication authentication,
            HttpServletRequest request
    ){

        AdminAuthPageResponse response = adminAuthorizationService.getAdmins(page, size);

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), response);
    }

    @PatchMapping("/auth/{userId}")
    public ApiResponse<Void> changeAdminAuth(
            @Positive @PathVariable Long userId,
            @Valid @RequestBody AdminChangeAuthRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        adminAccessValidator.requireRoot(authentication);
        adminAuthorizationService.changeAuth(userId, request);

        return ApiResponse.success(ResponseCode.UPDATED, httpRequest.getRequestURI(), null);
    }

    @DeleteMapping("/auth/{userId}")
    public ApiResponse<Void> deleteAdmin(
            @Positive @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        adminAccessValidator.requireRoot(authentication);
        adminAuthorizationService.deleteAuth(userId);

        return ApiResponse.success(ResponseCode.DELETED, httpRequest.getRequestURI(), null);
    }
}
