package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.web.CurrentUser;
import com.example.project.consultation.dto.request.TicketIssueRequest;
import com.example.project.consultation.dto.response.TicketIssueResponse;
import com.example.project.consultation.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}