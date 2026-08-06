package com.example.project.admin.controller;

import com.example.project.admin.dto.response.AdminMeResponse;
import com.example.project.admin.service.AdminAuthorizationService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@Api(tags = "관리자 인증 API", description = "현재 관리자 인증 및 권한 확인")
@ApiLog
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthorizationService adminAuthorizationService;

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
}
