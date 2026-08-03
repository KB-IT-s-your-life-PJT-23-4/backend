package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.dto.request.ConsultClarificationRequest;
import com.example.project.consultation.dto.request.ConsultRequest;
import com.example.project.consultation.dto.response.ConsultResponse;
import com.example.project.consultation.service.ConsultService;
import com.example.project.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ConsultController {

    private final ConsultService consultService;
    private final JwtProvider jwtProvider;

    @PostMapping("/consult")
    public ApiResponse<ConsultResponse> consult(
            @RequestBody ConsultRequest request,
            HttpServletRequest httpRequest
            // TODO 인증 사용자 정보 주입 방식 확정 후 userId 파라미터 교체
    ) {
        Long userId = null; // TODO 인증 확정 후 실제 값으로 교체
        ConsultResponse data = consultService.consult(request.question(), userId);
        return ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data);
    }

    @PostMapping("/consult/clarification")
    public ApiResponse<ConsultResponse> answerClarification(
            @RequestBody ConsultClarificationRequest request,
            HttpServletRequest httpRequest
            // TODO 인증 사용자 정보 주입 방식 확정 후 userId 파라미터 교체
    ) {
        Long userId = null; // TODO 인증 확정 후 실제 값으로 교체
        ConsultResponse data = consultService.answerClarification(request, userId);
        return ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data);
    }
}