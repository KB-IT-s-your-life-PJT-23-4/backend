package com.example.project.admin.controller;

import com.example.project.admin.dto.response.AdminDashboardResponse;
import com.example.project.admin.service.AdminDashboardService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@Api(tags = "관리자 대시보드 API", description = "서비스 핵심 운영 지표 조회")
@ApiLog
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @ApiOperation(
            value = "관리자 대시보드 조회",
            notes = "한국 시간 기준 최근 7일의 가입자·시뮬레이션 통계와 최신 상품 통계를 조회합니다."
    )
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard(HttpServletRequest request) {
        AdminDashboardResponse data = adminDashboardService.getDashboard();
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }
}
