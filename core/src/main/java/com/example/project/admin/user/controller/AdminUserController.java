package com.example.project.admin.user.controller;

import com.example.project.admin.user.dto.response.AdminUserPageResponse;
import com.example.project.admin.user.dto.response.AdminUserResponse;
import com.example.project.admin.user.service.AdminUserService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Api(tags = "관리자 회원 관리 API", description = "회원 검색과 서비스 이용 현황 조회")
@ApiLog
@Validated
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @ApiOperation(value = "관리자 회원 목록 조회", notes = "회원 ID, 이메일, 이름으로 검색합니다.")
    @GetMapping
    public ApiResponse<AdminUserPageResponse> getUsers(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        AdminUserPageResponse data = adminUserService.getUsers(
                userId,
                email,
                name,
                page,
                size
        );
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @ApiOperation(value = "관리자 회원 상세 조회", notes = "회원 기본 정보와 이용 건수를 조회합니다.")
    @GetMapping("/{userId}")
    public ApiResponse<AdminUserResponse> getUser(
            @PathVariable @Min(1) Long userId,
            HttpServletRequest request
    ) {
        AdminUserResponse data = adminUserService.getUser(userId);
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }
}
