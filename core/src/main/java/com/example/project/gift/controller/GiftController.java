package com.example.project.gift.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.web.CurrentUser;
import com.example.project.gift.domain.Status;
import com.example.project.gift.dto.request.GiftRequest;
import com.example.project.gift.dto.response.DeductionResponse;
import com.example.project.gift.dto.response.GiftResponse;
import com.example.project.gift.dto.request.GiftStatusRequest;
import com.example.project.gift.service.GiftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/gm")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    @PostMapping("/gift")
    public ApiResponse<GiftResponse> createGift(@RequestBody GiftRequest giftRequest, @AuthenticationPrincipal String principal, HttpServletRequest request) {
        GiftResponse data = giftService.createGift(giftRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @GetMapping("/gift")
    public ApiResponse<List<GiftResponse>> getAllGift(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Status status,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<GiftResponse> data = giftService.selectAllGift(familyId, status, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping("/deduction")
    public ApiResponse<List<DeductionResponse>> getDeduction(
            @RequestParam(required = false) Long familyId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<DeductionResponse> data = giftService.selectDeduction(familyId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping("/gift/{giftId}")
    public ApiResponse<GiftResponse> getGift(
            @PathVariable Long giftId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        GiftResponse data = giftService.selectGift(giftId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @PatchMapping("/gift/{giftId}")
    public ApiResponse<GiftResponse> updateGift(
            @PathVariable Long giftId,
            @RequestBody GiftRequest giftRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        GiftResponse data = giftService.updateGift(giftId, giftRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @PatchMapping("/gift/{giftId}/status")
    public ApiResponse<GiftResponse> updateGiftStatus(
            @PathVariable Long giftId,
            @RequestBody GiftStatusRequest giftStatusRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        GiftResponse data = giftService.updateGiftStatus(giftId, giftStatusRequest.getStatus(), CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @DeleteMapping("/gift/{giftId}")
    public ApiResponse<Void> deleteGift(
            @PathVariable Long giftId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        giftService.deleteGift(giftId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.DELETED, request.getRequestURI(), null);
    }
}
