package com.example.project.consultation.reservation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.reservation.client.KakaoLocalClient;
import com.example.project.consultation.reservation.dto.response.KakaoKeywordSearchResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;

@RestController
@Api(tags = "장소 검색 API", description = "카카오 로컬 API를 이용해 좌표 주변의 장소를 검색합니다.")
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class KakaoLocalController {

    private final KakaoLocalClient kakaoLocalClient;

    private static final long TIMEOUT_MS = 5_000L;

    @GetMapping("/search")
    @ApiOperation(value = "주변 장소 검색", notes = "검색어와 중심 좌표, 반경을 기준으로 카카오 장소 검색 결과를 반환합니다.")
    public DeferredResult<ApiResponse<KakaoKeywordSearchResponse>> search(
            @ApiParam(value = "장소 검색어", required = true, example = "국민은행") @RequestParam String query,
            @ApiParam(value = "중심 좌표 경도", required = true, example = "127.0276") @RequestParam double x,
            @ApiParam(value = "중심 좌표 위도", required = true, example = "37.4979") @RequestParam double y,
            @ApiParam(value = "검색 반경(미터)", defaultValue = "2000") @RequestParam(defaultValue = "2000") int radius,
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
