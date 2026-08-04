package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.dto.request.ConsultClarificationRequest;
import com.example.project.consultation.dto.request.ConsultRequest;
import com.example.project.consultation.dto.response.ConsultResponse;
import com.example.project.consultation.service.ConsultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ConsultController {

    private final ConsultService consultService;

    private static final long TIMEOUT_MS = 20_000L;

    @PostMapping("/consult")
    public DeferredResult<ApiResponse<ConsultResponse>> consult(
            @RequestBody ConsultRequest request,
            HttpServletRequest httpRequest
            // TODO 인증 사용자 정보 주입 방식 확정 후 파라미터 추가
    ) {
        DeferredResult<ApiResponse<ConsultResponse>> deferredResult = new DeferredResult<>(TIMEOUT_MS);
        Long userId = 1L; // TODO 인증 확정 후 실제 값으로 교체

        consultService.consult(request.question(), userId)
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data)
                        ),
                        deferredResult::setErrorResult // 예외를 그대로 넘기면 CommonExceptionAdvice가 처리
                );

        return deferredResult;
    }

    @PostMapping("/consult/clarification")
    public DeferredResult<ApiResponse<ConsultResponse>> answerClarification(
            @RequestBody ConsultClarificationRequest request,
            HttpServletRequest httpRequest
            // TODO 인증 사용자 정보 주입 방식 확정 후 파라미터 추가
    ) {
        DeferredResult<ApiResponse<ConsultResponse>> deferredResult = new DeferredResult<>(TIMEOUT_MS);
        Long userId = null; // TODO 인증 확정 후 실제 값으로 교체

        consultService.answerClarification(request, userId)
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data)
                        ),
                        deferredResult::setErrorResult
                );

        return deferredResult;
    }
}