package com.example.project.consultation.client;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.common.logging.ApiErrorTrackingContext;
import com.example.project.consultation.dto.fastapi.ChatRequest;
import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.example.project.consultation.dto.fastapi.ClarificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Log4j2
public class FastApiClient {

    private final WebClient fastApiWebClient;

    private static final String CHAT_URI = "/api/v1/chat";
    private static final String CLARIFICATION_URI = "/api/v1/chat/clarification";
    static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(25);

    public Mono<ChatResponse> startChat(ChatRequest request) {
        return fastApiWebClient.post()
                .uri(CHAT_URI)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(RESPONSE_TIMEOUT)
                .onErrorMap(this::mapError);
    }

    public Mono<ChatResponse> submitClarification(ClarificationRequest request) {
        return fastApiWebClient.post()
                .uri(CLARIFICATION_URI)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(RESPONSE_TIMEOUT)
                .onErrorMap(this::mapError);
    }

    private Throwable mapError(Throwable e) {
        if (e instanceof WebClientResponseException wcre) {
            log.error(
                    "FastAPI 오류 응답: status={}",
                    wcre.getStatusCode(),
                    e
            );
            return new ServiceException(
                    resolveResponseCode(wcre.getStatusCode().value()),
                    e
            );
        }
        if (e instanceof WebClientRequestException) {
            log.error("FastAPI 연결 실패: {}", e.getMessage(), e);
            ResponseCode responseCode = ApiErrorTrackingContext.isTimeout(e)
                    ? ResponseCode.EXTERNAL_API_TIMEOUT
                    : ResponseCode.EXTERNAL_API_ERROR;
            return new ServiceException(responseCode, e);
        }
        if (e instanceof java.util.concurrent.TimeoutException) {
            log.error("FastAPI 응답 타임아웃: {}", e.getMessage(), e);
            return new ServiceException(ResponseCode.EXTERNAL_API_TIMEOUT, e);
        }
        log.error("FastAPI 호출 중 알 수 없는 오류: {}", e.getMessage(), e);
        return new ServiceException(ResponseCode.EXTERNAL_API_ERROR, e);
    }

    // 오류코드
    private ResponseCode resolveResponseCode(int httpStatus) {
        return switch (httpStatus) {
            case 422 -> ResponseCode.VALIDATION_FAILED;
            case 404 -> ResponseCode.RESOURCE_NOT_FOUND;
            case 429 -> ResponseCode.EXTERNAL_API_ERROR;
            case 502 -> ResponseCode.EXTERNAL_API_ERROR;
            case 503 -> ResponseCode.SERVICE_UNAVAILABLE;
            case 504 -> ResponseCode.EXTERNAL_API_TIMEOUT;
            case 500 -> ResponseCode.FASTAPI_SERVER_ERROR;
            default -> ResponseCode.EXTERNAL_API_ERROR;
        };
    }
}
