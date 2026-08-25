package com.example.project.gift.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.common.web.CurrentUser;
import com.example.project.gift.domain.Status;
import com.example.project.gift.dto.request.GiftRequest;
import com.example.project.gift.dto.response.DeductionResponse;
import com.example.project.gift.dto.response.FilingInfoResponse;
import com.example.project.gift.dto.response.GiftResponse;
import com.example.project.gift.dto.request.GiftStatusRequest;
import com.example.project.gift.dto.request.SimulationGiftRequest;
import com.example.project.gift.service.GiftService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@ApiLog
@Api(tags = "증여 관리 API", description = "로그인 사용자의 증여 내역, 공제 현황 및 신고 정보를 관리합니다.")
@Log4j2
@RestController
@RequestMapping("/api/gm")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    @PostMapping("/gift")
    @ApiOperation(value = "증여 등록", notes = "수증자에게 진행할 증여 내역을 등록합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<GiftResponse> createGift(@RequestBody GiftRequest giftRequest, @AuthenticationPrincipal String principal, HttpServletRequest request) {
        GiftResponse data = giftService.createGift(giftRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    /** 저장된 시뮬레이션을 진행 중인 증여로 등록한다. 분할 증여면 회차 수만큼 생성되어 목록으로 돌아온다. */
    @PostMapping("/gift/from-simulation")
    @ApiOperation(value = "시뮬레이션 결과로 증여 등록", notes = "저장된 시뮬레이션 결과를 진행 중인 증여 내역으로 등록합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<List<GiftResponse>> registerGiftFromSimulation(
            @RequestBody SimulationGiftRequest simulationGiftRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<GiftResponse> data = giftService.registerFromSimulation(simulationGiftRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @GetMapping("/gift")
    @ApiOperation(value = "증여 목록 조회", notes = "수증자와 진행 상태 조건으로 증여 내역을 조회합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<List<GiftResponse>> getAllGift(
            @ApiParam(value = "수증자 ID. 생략하면 전체 수증자를 조회합니다.") @RequestParam(required = false) Long familyId,
            @ApiParam(value = "증여 진행 상태. 생략하면 전체 상태를 조회합니다.") @RequestParam(required = false) Status status,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<GiftResponse> data = giftService.selectAllGift(familyId, status, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping("/deduction")
    @ApiOperation(value = "증여재산 공제 현황 조회", notes = "수증자별 사용 공제액과 다음 공제 갱신일을 조회합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<List<DeductionResponse>> getDeduction(
            @ApiParam(value = "수증자 ID. 생략하면 전체 수증자를 조회합니다.") @RequestParam(required = false) Long familyId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<DeductionResponse> data = giftService.selectDeduction(familyId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping("/gift/{giftId}")
    @ApiOperation(value = "증여 상세 조회", notes = "본인이 소유한 증여 내역 한 건을 조회합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<GiftResponse> getGift(
            @ApiParam(value = "증여 ID", required = true, example = "1") @PathVariable Long giftId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        GiftResponse data = giftService.selectGift(giftId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @PatchMapping("/gift/{giftId}")
    @ApiOperation(value = "증여 정보 수정", notes = "본인이 소유한 증여 내역의 금액, 날짜 및 메모를 수정합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<GiftResponse> updateGift(
            @ApiParam(value = "증여 ID", required = true, example = "1") @PathVariable Long giftId,
            @RequestBody GiftRequest giftRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        GiftResponse data = giftService.updateGift(giftId, giftRequest, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @PatchMapping("/gift/{giftId}/status")
    @ApiOperation(value = "증여 상태 변경", notes = "증여 내역의 진행 상태를 변경합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<GiftResponse> updateGiftStatus(
            @ApiParam(value = "증여 ID", required = true, example = "1") @PathVariable Long giftId,
            @RequestBody GiftStatusRequest giftStatusRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        GiftResponse data = giftService.updateGiftStatus(giftId, giftStatusRequest.getStatus(), CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @DeleteMapping("/gift/{giftId}")
    @ApiOperation(value = "증여 삭제", notes = "본인이 소유한 증여 내역을 삭제합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<Void> deleteGift(
            @ApiParam(value = "증여 ID", required = true, example = "1") @PathVariable Long giftId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        giftService.deleteGift(giftId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.DELETED, request.getRequestURI(), null);
    }

    // 공제
    @GetMapping("/gift/{giftId}/filing-info")
    @ApiOperation(value = "증여 신고 정보 조회", notes = "선택한 증여의 신고 기한과 신고에 필요한 정보를 조회합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<FilingInfoResponse> getFilingInfo(
            @ApiParam(value = "증여 ID", required = true, example = "1") @PathVariable Long giftId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        FilingInfoResponse data = giftService.getFilingInfo(giftId, CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }
}
