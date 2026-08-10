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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
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
    public ApiResponse<TicketStatusResponse> status(
            @RequestParam Long branchId,
            HttpServletRequest httpRequest
    ) {
        TicketStatusResponse data = ticketService.getTicketStatus(branchId);
        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data);
    }
}