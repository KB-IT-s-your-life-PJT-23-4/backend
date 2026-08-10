package com.example.project.consultation.reservation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.reservation.client.KakaoLocalClient;
import com.example.project.consultation.reservation.dto.response.KakaoKeywordSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class KakaoLocalController {

    private final KakaoLocalClient kakaoLocalClient;

    private static final long TIMEOUT_MS = 5_000L;

    @GetMapping("/search")
    public DeferredResult<ApiResponse<KakaoKeywordSearchResponse>> search(
            @RequestParam String query,
            @RequestParam double x,
            @RequestParam double y,
            @RequestParam(defaultValue = "2000") int radius,
            HttpServletRequest httpRequest
    ) {
        DeferredResult<ApiResponse<KakaoKeywordSearchResponse>> deferredResult = new DeferredResult<>(TIMEOUT_MS);

        kakaoLocalClient.searchKeyword(query, x, y, radius)
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data)
                        ),
                        deferredResult::setErrorResult
                );

        return deferredResult;
    }
}