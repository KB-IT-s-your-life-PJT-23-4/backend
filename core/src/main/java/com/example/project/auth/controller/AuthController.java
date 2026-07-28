package com.example.project.auth.controller;

import com.example.project.auth.dto.request.LoginRequest;
import com.example.project.auth.dto.request.LogoutRequest;
import com.example.project.auth.dto.request.TokenRefreshRequest;
import com.example.project.auth.dto.response.AuthTokenResponse;
import com.example.project.auth.service.AuthService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

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
        authService.logout(request, resolveAccessToken(authorizationHeader));
        return ApiResponse.success(ResponseCode.LOGOUT_SUCCESS, httpRequest.getRequestURI(), null);
    }

    private String resolveAccessToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        return token;
    }
}
