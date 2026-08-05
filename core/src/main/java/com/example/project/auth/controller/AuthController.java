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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Api(tags = "인증 API", description = "로그인, Access/Refresh Token 재발급 및 로그아웃")
@ApiLog
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @ApiOperation(
            value = "로그인",
            notes = "이메일과 비밀번호를 검증하고 Access Token과 Refresh Token을 발급합니다."
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(
                    code = 200,
                    message = "로그인 성공"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 400,
                    message = "요청값 검증 실패",
                    response = ApiResponse.class
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 401,
                    message = "이메일 또는 비밀번호 불일치",
                    response = ApiResponse.class
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 500,
                    message = "서버 내부 오류",
                    response = ApiResponse.class
            )
    })
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthTokenResponse data = authService.login(request);
        return ApiResponse.success(ResponseCode.LOGIN_SUCCESS, httpRequest.getRequestURI(), data);
    }

    @PostMapping("/refresh")
    @ApiOperation(
            value = "토큰 재발급",
            notes = "유효한 Refresh Token을 회전시키고 새 Access Token과 Refresh Token을 발급합니다. "
                    + "기존 Refresh Token은 즉시 폐기됩니다."
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "토큰 재발급 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "토큰 만료 또는 유효하지 않은 Refresh Token", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "토큰 사용자를 찾을 수 없음", response = ApiResponse.class)
    })
    public ApiResponse<AuthTokenResponse> refresh(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthTokenResponse data = authService.refresh(request);
        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data);
    }

    @PostMapping("/logout")
    @ApiOperation(
            value = "로그아웃",
            notes = "Authorization 헤더의 Access Token과 요청 본문의 Refresh Token을 모두 폐기합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "로그아웃 성공(body statusCode 206)"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Access/Refresh Token 불일치 또는 인증 실패", response = ApiResponse.class)
    })
    public ApiResponse<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @ApiParam(value = "Bearer {accessToken}", required = true)
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest httpRequest
    ) {
        authService.logout(request, JwtUtil.resolveRequiredAccessToken(authorizationHeader));
        return ApiResponse.success(ResponseCode.LOGOUT_SUCCESS, httpRequest.getRequestURI(), null);
    }
}
