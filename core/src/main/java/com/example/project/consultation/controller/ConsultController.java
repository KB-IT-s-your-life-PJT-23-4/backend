package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.common.logging.ApiLog;
import com.example.project.common.web.CurrentUser;
import com.example.project.consultation.dto.request.ConsultClarificationRequest;
import com.example.project.consultation.dto.request.ConsultRequest;
import com.example.project.consultation.dto.response.ConsultResponse;
import com.example.project.consultation.dto.response.ConversationHistoryResponse;
import com.example.project.consultation.service.ConsultService;
import com.example.project.user.service.AccountAccessService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import reactor.core.Disposable;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;

@ApiLog
@Api(tags = "AI 상담 API")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ConsultController {

    private final ConsultService consultService;
    private final AccountAccessService accountAccessService;

    private static final long TIMEOUT_MS = 35_000L;

    @ApiOperation(
            value = "AI 상담 시작",
            notes = "사용자의 질문을 FastAPI 서버에 전달"
    )
    @PostMapping("/consult")
    public DeferredResult<ApiResponse<ConsultResponse>> consult(
            @Valid @RequestBody ConsultRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal String principal
    ) {
        DeferredResult<ApiResponse<ConsultResponse>> deferredResult = new DeferredResult<>(TIMEOUT_MS);
        Long userId = CurrentUser.id(principal);
        accountAccessService.requireRestrictedFeatureAccess(userId);

        Disposable subscription = consultService.consult(request.question(), userId)
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data)
                        ),
                        deferredResult::setErrorResult // 예외를 그대로 넘기면 CommonExceptionAdvice가 처리
                );

        configureTimeout(deferredResult, subscription);

        return deferredResult;
    }

    @ApiOperation(
            value = "AI 상담 추가 질문",
            notes = "사용자의 질문 외에 더 필요한 정보 응답 후 FastAPI 서버 전달"
    )
    @PostMapping("/consult/clarification")
    public DeferredResult<ApiResponse<ConsultResponse>> answerClarification(
            @Valid @RequestBody ConsultClarificationRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal String principal
    ) {
        DeferredResult<ApiResponse<ConsultResponse>> deferredResult = new DeferredResult<>(TIMEOUT_MS);
        Long userId = CurrentUser.id(principal);
        accountAccessService.requireRestrictedFeatureAccess(userId);

        Disposable subscription = consultService.answerClarification(request, userId)
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(ResponseCode.AI_RESPONSE_SUCCESS, httpRequest.getRequestURI(), data)
                        ),
                        deferredResult::setErrorResult
                );

        configureTimeout(deferredResult, subscription);

        return deferredResult;
    }

    @GetMapping("/consult/history")
    public DeferredResult<ApiResponse<ConversationHistoryResponse>> getHistory(
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal String principal
    ) {
        DeferredResult<ApiResponse<ConversationHistoryResponse>> deferredResult
                = new DeferredResult<>(TIMEOUT_MS);

        Long userId = CurrentUser.id(principal);

        accountAccessService.requireRestrictedFeatureAccess(userId);

        Disposable subscription = consultService.getHistory(userId)
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(
                                        ResponseCode.SUCCESS,
                                        httpRequest.getRequestURI(),
                                        data
                                )
                        ),
                        deferredResult::setErrorResult
                );

        configureTimeout(deferredResult, subscription);

        return deferredResult;
    }

    private <T> void configureTimeout(
            DeferredResult<ApiResponse<T>> deferredResult,
            Disposable subscription
    ) {
        deferredResult.onTimeout(() -> {
            subscription.dispose();
            deferredResult.setErrorResult(
                    new ServiceException(ResponseCode.REQUEST_TIMEOUT)
            );
        });
        deferredResult.onCompletion(subscription::dispose);
    }
}
