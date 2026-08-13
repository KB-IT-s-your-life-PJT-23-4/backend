package com.example.project.admin.user.controller;

import com.example.project.admin.user.dto.request.AdminUserBlockRequest;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
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

    @ApiOperation(
            value = "관리자 회원 목록 조회",
            notes = "회원 ID 또는 전체 이메일의 정확 일치로 검색합니다. 이름 및 이메일 부분 검색은 지원하지 않습니다."
    )
    @GetMapping
    public ApiResponse<AdminUserPageResponse> getUsers(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
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

    @ApiOperation(value = "관리자 회원 차단", notes = "ROOT 또는 MIDDLE 관리자가 일반 회원을 지정 시각까지 차단합니다.")
    @PatchMapping("/{userId}/block")
    public ApiResponse<AdminUserResponse> blockUser(
            @PathVariable @Min(1) Long userId,
            @Valid @RequestBody AdminUserBlockRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        AdminUserResponse data = adminUserService.blockUser(
                authentication,
                userId,
                body.getBlockedUntil()
        );
        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @ApiOperation(value = "관리자 회원 차단 해제", notes = "ROOT 또는 MIDDLE 관리자가 일반 회원의 차단을 해제합니다.")
    @PatchMapping("/{userId}/unblock")
    public ApiResponse<AdminUserResponse> unblockUser(
            @PathVariable @Min(1) Long userId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        AdminUserResponse data = adminUserService.unblockUser(authentication, userId);
        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @ApiOperation(
            value = "관리자 회원 삭제",
            notes = "기존 회원탈퇴 로직을 재사용하여 회원 개인정보와 모든 연관 데이터를 삭제합니다."
    )
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(
            @PathVariable @Min(1) Long userId,
            HttpServletRequest request
    ) {
        adminUserService.deleteUser(userId);
        return ApiResponse.success(ResponseCode.DELETED, request.getRequestURI(), null);
    }
}
