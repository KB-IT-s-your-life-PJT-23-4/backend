package com.example.project.common.kakao.client;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.common.kakao.dto.KakaoKeywordSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Log4j2
public class KakaoLocalClient {

    private final WebClient kakaoWebClient;

    private static final String KEYWORD_SEARCH_URI = "/v2/local/search/keyword.json";

    public Mono<KakaoKeywordSearchResponse> searchKeyword(String query, double x, double y, int radius) {
        return kakaoWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(KEYWORD_SEARCH_URI)
                        .queryParam("query", query)
                        .queryParam("x", x)
                        .queryParam("y", y)
                        .queryParam("radius", radius)
                        .build())
                .retrieve()
                .bodyToMono(KakaoKeywordSearchResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(this::mapError);
    }

    private Throwable mapError(Throwable e) {
        if (e instanceof WebClientResponseException wcre) {
            log.error("카카오 로컬 API 오류 응답: status={}, body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString(), e);
            return new ServiceException(ResponseCode.EXTERNAL_API_ERROR);
        }
        if (e instanceof WebClientRequestException) {
            log.error("카카오 로컬 API 연결 실패: {}", e.getMessage(), e);
            return new ServiceException(ResponseCode.EXTERNAL_API_TIMEOUT);
        }
        if (e instanceof TimeoutException) {
            log.error("카카오 로컬 API 응답 타임아웃: {}", e.getMessage(), e);
            return new ServiceException(ResponseCode.EXTERNAL_API_TIMEOUT);
        }
        log.error("카카오 로컬 API 호출 중 알 수 없는 오류: {}", e.getMessage(), e);
        return new ServiceException(ResponseCode.EXTERNAL_API_ERROR);
    }
}