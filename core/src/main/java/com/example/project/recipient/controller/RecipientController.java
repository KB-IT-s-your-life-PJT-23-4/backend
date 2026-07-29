package com.example.project.recipient.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.web.CurrentUser;
import com.example.project.recipient.dto.RecipientRequest;
import com.example.project.recipient.dto.RecipientResponse;
import com.example.project.recipient.service.RecipientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/gm/families")
@Log4j2
@RequiredArgsConstructor
public class RecipientController {

    private final RecipientService recipientService;

    @PostMapping
    public ApiResponse<RecipientResponse> createRecipient(
            @RequestBody RecipientRequest recipientRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        RecipientResponse data = recipientService.createRecipient(recipientRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @PatchMapping("/{familyId}")
    public ApiResponse<RecipientResponse> updateRecipient(
            @PathVariable Long familyId,
            @RequestBody RecipientRequest recipientRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        RecipientResponse data = recipientService.updateRecipient(familyId, recipientRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    /**
     * 증여 이력이 있으면 409 로 막는다. 프론트가 경고를 띄우고 force=true 로 다시 부르면 진행한다.
     */
    @DeleteMapping("/{familyId}")
    public ApiResponse<Void> deleteRecipient(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        recipientService.deleteRecipient(familyId, CurrentUser.id(principal), force);

        return ApiResponse.success(ResponseCode.DELETED, request.getRequestURI(), null);
    }

    @GetMapping("/{familyId}")
    public ApiResponse<RecipientResponse> getRecipient(@PathVariable Long familyId, @AuthenticationPrincipal String principal,
                                                       HttpServletRequest request) {
        RecipientResponse data = recipientService.selectRecipient(familyId, CurrentUser.id(principal));
        log.info("data: " + data);

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping
    public ApiResponse<List<RecipientResponse>> getRecipients(@AuthenticationPrincipal String principal, HttpServletRequest request) {
        List<RecipientResponse> data = recipientService.selectAllRecipient(CurrentUser.id(principal));
        log.info("list: " + data);

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

}
