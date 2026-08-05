package com.example.project.user.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.common.web.CurrentUser;
import com.example.project.user.dto.UserDTO;
import com.example.project.user.dto.request.UserProfileUpdateRequest;
import com.example.project.user.dto.request.UserSignupRequest;
import com.example.project.user.dto.request.UserUpdateRequest;
import com.example.project.user.dto.response.EmailAvailabilityResponse;
import com.example.project.user.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Api(tags = "회원 API", description = "회원가입, 이메일 중복 확인, 내 정보 조회·수정 및 회원탈퇴")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Log4j2
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation(
            value = "회원가입",
            notes = "이메일을 소문자로 정규화하고 비밀번호를 BCrypt로 암호화해 회원을 등록합니다."
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 201, message = "회원가입 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이메일 또는 전화번호 중복", response = ApiResponse.class)
    })
    public ApiResponse<UserDTO> signup(
            @Valid @RequestBody UserSignupRequest signupRequest,
            HttpServletRequest httpRequest
    ) {
        UserDTO createdUser = userService.signup(signupRequest);
        log.info("User signup completed: userId={}", createdUser.getUserId());

        return ApiResponse.success(ResponseCode.CREATED, httpRequest.getRequestURI(), createdUser);
    }

    @GetMapping("/auth/check-email")
    @ApiOperation(value = "이메일 중복 확인", notes = "정규화된 이메일의 사용 가능 여부를 반환합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "중복 확인 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "이메일 형식 검증 실패", response = ApiResponse.class)
    })
    public ApiResponse<EmailAvailabilityResponse> checkEmail(
            @RequestParam
            @ApiParam(value = "확인할 이메일", required = true, example = "user@example.com")
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
    @ApiOperation(
            value = "내 정보 조회",
            notes = "Access Token의 사용자 ID로 회원 정보를 조회합니다. 비밀번호는 응답하지 않습니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "내 정보 조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "회원 정보 없음", response = ApiResponse.class)
    })
    public ApiResponse<UserDTO> getMyProfile(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(authentication);
        UserDTO userProfile = userService.getProfile(userId);
        log.debug("User profile retrieved: userId={}", userId);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), userProfile);
    }

    @PutMapping(value = "/users/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(
            value = "내 정보 수정(JSON)",
            notes = "이메일, 이름, 생년월일, 전화번호와 이미지 경로를 JSON으로 수정하는 기존 API입니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "수정 성공(body statusCode 203)"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "회원 정보 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이메일 또는 전화번호 중복", response = ApiResponse.class)
    })
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

    @PutMapping(value = "/users/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(
            value = "내 정보 수정(multipart)",
            notes = "profile에는 application/json 형식의 이름·생년월일·전화번호를, image에는 선택한 이미지 파일을 전송합니다. "
                    + "removeImage=true이면 기존 이미지를 삭제하며 image와 동시에 사용할 수 없습니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "수정 성공(body statusCode 203)"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 또는 이미지 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "회원 정보 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 409, message = "전화번호 중복", response = ApiResponse.class)
    })
    public ApiResponse<UserDTO> updateMyEditableProfile(
            Authentication authentication,
            @ApiParam(value = "수정할 프로필 JSON", required = true)
            @Valid @RequestPart("profile") UserProfileUpdateRequest updateRequest,
            @ApiParam(value = "JPG, PNG, GIF 또는 WebP 이미지(최대 10MB)")
            @RequestPart(value = "image", required = false) MultipartFile image,
            @ApiParam(value = "기존 프로필 이미지 삭제 여부", defaultValue = "false")
            @RequestParam(defaultValue = "false") boolean removeImage,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(authentication);
        UserDTO updatedUser = userService.updateEditableProfile(
                userId,
                updateRequest,
                image,
                removeImage
        );
        log.info("User editable profile updated: userId={}, imageChanged={}",
                userId, image != null || removeImage);

        return ApiResponse.success(ResponseCode.UPDATED, httpRequest.getRequestURI(), updatedUser);
    }

    @DeleteMapping("/users/me")
    @ApiOperation(
            value = "회원탈퇴",
            notes = "Access Token의 회원과 ON DELETE CASCADE로 연결된 사용자 데이터를 삭제합니다. "
                    + "클라이언트는 성공 후 로그아웃 API로 토큰을 폐기해야 합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "회원탈퇴 성공(body statusCode 204)"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "회원 정보 없음", response = ApiResponse.class)
    })
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

        return CurrentUser.id(authentication.getName());
    }
}
