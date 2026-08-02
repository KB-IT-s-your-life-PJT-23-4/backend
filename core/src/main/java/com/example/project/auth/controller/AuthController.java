package com.example.project.auth.controller;

import com.example.project.auth.dto.request.LoginRequest;
import com.example.project.auth.dto.request.LogoutRequest;
import com.example.project.auth.dto.request.TokenRefreshRequest;
import com.example.project.auth.dto.response.AuthTokenResponse;
import com.example.project.auth.service.AuthService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@ApiLog
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthTokenResponse data = authService.login(request);
        return ApiResponse.success(ResponseCode.LOGIN_SUCCESS, httpRequest.getRequestURI(), data);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthTokenResponse data = authService.refresh(request);
        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpRequest
    ) {
        authService.logout(request, JwtUtil.resolveRequiredAccessToken(authorizationHeader));
        return ApiResponse.success(ResponseCode.LOGOUT_SUCCESS, httpRequest.getRequestURI(), null);
    }
}
