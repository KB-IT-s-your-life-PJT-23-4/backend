package com.example.project.user.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserSignupRequest;
import com.example.project.user.dto.request.UserUpdateRequest;
import com.example.project.user.dto.response.EmailAvailabilityResponse;
import com.example.project.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDTO> signup(
            @Valid @RequestBody UserSignupRequest request,
            HttpServletRequest httpRequest
    ) {
        UserDTO data = userService.signup(request);
        return ApiResponse.success(ResponseCode.CREATED, httpRequest.getRequestURI(), data);
    }

    @GetMapping("/auth/check-email")
    public ApiResponse<EmailAvailabilityResponse> checkEmail(
            @RequestParam
            @NotBlank(message = "이메일은 필수입니다")
            @Email(message = "올바른 이메일 형식이 아닙니다")
            @Size(max = 100, message = "이메일은 100자 이하여야 합니다")
            String email,
            HttpServletRequest httpRequest
    ) {
        EmailAvailabilityResponse data = userService.checkEmailAvailability(email);
        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data);
    }

    @GetMapping("/users/me")
    public ApiResponse<UserDTO> getMyProfile(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        UserDTO data = userService.getProfile(resolveUserId(authentication));
        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data);
    }

    @PutMapping("/users/me")
    public ApiResponse<UserDTO> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        UserDTO data = userService.updateProfile(resolveUserId(authentication), request);
        return ApiResponse.success(ResponseCode.UPDATED, httpRequest.getRequestURI(), data);
    }

    @DeleteMapping("/users/me")
    public ApiResponse<Void> deleteMyAccount(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        userService.deleteUser(resolveUserId(authentication));
        return ApiResponse.success(ResponseCode.DELETED, httpRequest.getRequestURI(), null);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }
    }
}
