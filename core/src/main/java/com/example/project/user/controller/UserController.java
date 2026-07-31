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
import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDTO> signup(
            @Valid @RequestBody UserSignupRequest signupRequest,
            HttpServletRequest httpRequest
    ) {
        UserDTO createdUser = userService.signup(signupRequest);
        log.info("User signup completed: userId={}", createdUser.getUserId());

        return ApiResponse.success(ResponseCode.CREATED, httpRequest.getRequestURI(), createdUser);
    }

    @GetMapping("/auth/check-email")
    public ApiResponse<EmailAvailabilityResponse> checkEmail(
            @RequestParam
            @NotBlank(message = "이메일은 필수입니다")
            @Email(message = "올바른 이메일 형식이 아닙니다")
            @Size(max = 255, message = "이메일은 255자 이하여야 합니다")
            String email,
            HttpServletRequest httpRequest
    ) {
        EmailAvailabilityResponse emailAvailability = userService.checkEmailAvailability(email);
        log.debug("Email availability checked: available={}", emailAvailability.available());

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), emailAvailability);
    }

    @GetMapping("/users/me")
    public ApiResponse<UserDTO> getMyProfile(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(authentication);
        UserDTO userProfile = userService.getProfile(userId);
        log.debug("User profile retrieved: userId={}", userId);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), userProfile);
    }

    @PutMapping("/users/me")
    public ApiResponse<UserDTO> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UserUpdateRequest updateRequest,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(authentication);
        UserDTO updatedUser = userService.updateProfile(userId, updateRequest);
        log.info("User profile updated: userId={}", userId);

        return ApiResponse.success(ResponseCode.UPDATED, httpRequest.getRequestURI(), updatedUser);
    }

    @DeleteMapping("/users/me")
    public ApiResponse<Void> deleteMyAccount(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(authentication);
        userService.deleteUser(userId);
        log.info("User account deleted: userId={}", userId);

        return ApiResponse.success(ResponseCode.DELETED, httpRequest.getRequestURI(), null);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }
    }
}
