package com.example.project.recipient.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.web.CurrentUser;
import com.example.project.recipient.dto.request.RecipientProfileUpdateRequest;
import com.example.project.recipient.dto.request.RecipientRequest;
import com.example.project.recipient.dto.response.RecipientResponse;
import com.example.project.recipient.service.RecipientService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "수증자 API", description = "로그인 회원이 소유한 수증자 등록·조회·수정·삭제")
@RestController
@RequestMapping("/api/fm/family")
@Log4j2
@RequiredArgsConstructor
public class RecipientController {

    private final RecipientService recipientService;

    @PostMapping
    @ApiOperation(
            value = "수증자 등록",
            notes = "relation은 LINEAL_DESCENDANT 또는 OTHER만 허용합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "등록 성공(body statusCode 201)"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class)
    })
    public ApiResponse<RecipientResponse> createRecipient(
            @Valid @RequestBody RecipientRequest recipientRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        RecipientResponse data = recipientService.createRecipient(recipientRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @PatchMapping(value = "/{familyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(
            value = "수증자 정보 부분 수정(JSON)",
            notes = "요청 본문에 포함된 이름·관계·생년월일·이미지 경로만 수정합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "수정 성공(body statusCode 203)"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인 소유 수증자 없음", response = ApiResponse.class)
    })
    public ApiResponse<RecipientResponse> updateRecipient(
            @ApiParam(value = "수증자 ID", required = true, example = "1") @PathVariable Long familyId,
            @Valid @RequestBody RecipientRequest recipientRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        RecipientResponse data = recipientService.updateRecipient(familyId, recipientRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @PatchMapping(value = "/{familyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(
            value = "수증자 프로필 수정(multipart)",
            notes = "profile에는 application/json 형식의 이름·생년월일을 전송합니다. 관계는 유지됩니다. "
                    + "removeImage=true이면 기존 이미지를 삭제합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "수정 성공(body statusCode 203)"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 또는 이미지 검증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인 소유 수증자 없음", response = ApiResponse.class)
    })
    public ApiResponse<RecipientResponse> updateEditableRecipient(
            @ApiParam(value = "수증자 ID", required = true, example = "1") @PathVariable Long familyId,
            @ApiParam(value = "수정할 수증자 프로필 JSON", required = true)
            @Valid @RequestPart("profile") RecipientProfileUpdateRequest recipientRequest,
            @ApiParam(value = "JPG, PNG, GIF 또는 WebP 이미지(최대 10MB)")
            @RequestPart(value = "image", required = false) MultipartFile image,
            @ApiParam(value = "기존 수증자 이미지 삭제 여부", defaultValue = "false")
            @RequestParam(defaultValue = "false") boolean removeImage,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        RecipientResponse data = recipientService.updateEditableProfile(
                familyId,
                recipientRequest,
                image,
                removeImage,
                CurrentUser.id(principal)
        );

        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    /**
     * 증여 이력이 있으면 409 로 막는다. 프론트가 경고를 띄우고 force=true 로 다시 부르면 진행한다.
     */
    @DeleteMapping("/{familyId}")
    @ApiOperation(
            value = "수증자 삭제",
            notes = "증여 이력이 있으면 409를 반환합니다. 경고 확인 후 force=true로 다시 요청하면 관련 이력과 함께 삭제합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "삭제 성공(body statusCode 204)"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인 소유 수증자 없음", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 409, message = "증여 이력이 있어 기본 삭제 차단", response = ApiResponse.class)
    })
    public ApiResponse<Void> deleteRecipient(
            @ApiParam(value = "수증자 ID", required = true, example = "1") @PathVariable Long familyId,
            @ApiParam(value = "증여 이력이 있어도 강제로 삭제할지 여부", defaultValue = "false")
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        recipientService.deleteRecipient(familyId, CurrentUser.id(principal), force);

        return ApiResponse.success(ResponseCode.DELETED, request.getRequestURI(), null);
    }

    @GetMapping("/{familyId}")
    @ApiOperation(
            value = "수증자 상세 조회",
            notes = "로그인 회원이 소유한 수증자 한 명을 조회합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "상세 조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인 소유 수증자 없음", response = ApiResponse.class)
    })
    public ApiResponse<RecipientResponse> getRecipient(
            @ApiParam(value = "수증자 ID", required = true, example = "1") @PathVariable Long familyId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        RecipientResponse data = recipientService.selectRecipient(familyId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping
    @ApiOperation(
            value = "수증자 목록 조회",
            notes = "로그인 회원이 소유한 수증자 목록을 familyId 오름차순으로 조회합니다.",
            authorizations = @io.swagger.annotations.Authorization("Bearer")
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "목록 조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 실패", response = ApiResponse.class)
    })
    public ApiResponse<List<RecipientResponse>> getRecipients(@AuthenticationPrincipal String principal, HttpServletRequest request) {
        List<RecipientResponse> data = recipientService.selectAllRecipient(CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }
}
