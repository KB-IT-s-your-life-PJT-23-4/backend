package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.common.web.CurrentUser;
import com.example.project.consultation.dto.request.ConsultClarificationRequest;
import com.example.project.consultation.dto.request.ConsultRequest;
import com.example.project.consultation.dto.response.ConsultResponse;
import com.example.project.consultation.service.ConsultService;
import com.example.project.security.JwtProvider;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@Api(tags = "AI 상담 API")
@ApiLog
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ConsultController {

    private final ConsultService consultService;

    @ApiOperation(
            value = "첫 질문 시작",
            notes = "사용자의 첫 질문을 받아 FastAPI 서버에 질문을 전송한다."
    )
    @PostMapping("/consult")
    public ApiResponse<ConsultResponse> consult(
            @RequestBody ConsultRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal String principal

    ) {
        Long userId = CurrentUser.id(principal);
        ConsultResponse data = consultService.consult(request.question(), userId);
        return ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data);
    }

    @PostMapping("/consult/clarification")
    public ApiResponse<ConsultResponse> answerClarification(
            @RequestBody ConsultClarificationRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal String principal
    ) {
        Long userId = CurrentUser.id(principal);
        ConsultResponse data = consultService.answerClarification(request, userId);
        return ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data);
    }
}