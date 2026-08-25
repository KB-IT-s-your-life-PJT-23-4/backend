package com.example.project.consultation.reservation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.web.CurrentUser;
import com.example.project.consultation.reservation.dto.request.TicketCallRequest;
import com.example.project.consultation.reservation.dto.request.TicketIssueRequest;
import com.example.project.consultation.reservation.dto.response.TicketCallResponse;
import com.example.project.consultation.reservation.dto.response.TicketIssueResponse;
import com.example.project.consultation.reservation.dto.response.TicketStatusResponse;
import com.example.project.consultation.reservation.service.TicketService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@Api(tags = "영업점 대기표 API", description = "KB국민은행 영업점 대기표 발급, 호출 및 대기 현황 조회 기능을 제공합니다.")
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @ApiOperation(value = "대기표 발급", notes = "로그인 사용자에게 선택한 영업점과 업무 유형의 대기표를 발급합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<TicketIssueResponse> issue(
            @Valid @RequestBody TicketIssueRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal String principal
    ) {
        Long userId = CurrentUser.id(principal);
        TicketIssueResponse data = ticketService.issueTicket(request.branchId(), request.serviceType(), userId);
        return ApiResponse.success(ResponseCode.CREATED, httpRequest.getRequestURI(), data);
    }

    @PostMapping("/call")
    @ApiOperation(value = "다음 대기표 호출", notes = "선택한 영업점에서 대기 중인 다음 번호를 호출합니다.", authorizations = @io.swagger.annotations.Authorization("Bearer"))
    public ApiResponse<TicketCallResponse> callNext(
            @Valid @RequestBody TicketCallRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal String principal
    ) {
        CurrentUser.id(principal); // 로그인 여부만 확인
        TicketCallResponse data = ticketService.callNextTicket(request.branchId());
        return ApiResponse.success(ResponseCode.UPDATED, httpRequest.getRequestURI(), data);
    }
    @GetMapping("/status")
    @ApiOperation(value = "영업점 대기 현황 조회", notes = "선택한 영업점의 현재 호출 번호와 대기 인원을 조회합니다.")
    public ApiResponse<TicketStatusResponse> status(
            @ApiParam(value = "영업점 ID", required = true, example = "1") @RequestParam Long branchId,
            HttpServletRequest httpRequest
    ) {
        TicketStatusResponse data = ticketService.getTicketStatus(branchId);
        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data);
    }
}
