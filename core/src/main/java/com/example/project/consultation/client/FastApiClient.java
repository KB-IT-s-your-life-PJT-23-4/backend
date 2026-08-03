package com.example.project.consultation.client;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.dto.fastapi.ChatRequest;
import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.example.project.consultation.dto.fastapi.ClarificationRequest;
import com.example.project.consultation.dto.fastapi.FastApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@Log4j2
public class FastApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper fastApiObjectMapper;

    @Value("${fastapi.base-url}")
    private String fastApiBaseUrl;

    private static final String CHAT_URI = "/api/v1/chat";
    private static final String CLARIFICATION_URI = "/api/v1/chat/clarification";

    public FastApiClient(RestTemplate fastApiRestTemplate, ObjectMapper fastApiObjectMapper) {
        this.restTemplate = fastApiRestTemplate;
        this.fastApiObjectMapper = fastApiObjectMapper;
    }

    public ChatResponse startChat(ChatRequest request) {
        return execute(() -> restTemplate.postForObject(
                fastApiBaseUrl + CHAT_URI, request, ChatResponse.class
        ));
    }

    public ChatResponse submitClarification(ClarificationRequest request) {
        return execute(() -> restTemplate.postForObject(
                fastApiBaseUrl + CLARIFICATION_URI, request, ChatResponse.class
        ));
    }

    private ChatResponse execute(java.util.function.Supplier<ChatResponse> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            // 연결 실패, 커넥션/리드 타임아웃
            log.error("FastAPI 연결 실패: {}", e.getMessage(), e);
            throw new ServiceException(ResponseCode.EXTERNAL_API_TIMEOUT);
        } catch (HttpStatusCodeException e) {
            logFastApiError(e);
            throw new ServiceException(resolveResponseCode(e.getStatusCode().value()));
        }
    }

    private void logFastApiError(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        try {
            FastApiErrorResponse errorResponse = fastApiObjectMapper.readValue(body, FastApiErrorResponse.class);
            log.error("FastAPI 오류: status={}, code={}, message={}",
                    e.getStatusCode(), errorResponse.error().code(), errorResponse.error().message());
        } catch (Exception parseError) {
            log.error("FastAPI 오류 (파싱 실패): status={}, body={}", e.getStatusCode(), body);
        }
    }

    private ResponseCode resolveResponseCode(int httpStatus) {
        return switch (httpStatus) {
            case 422 -> ResponseCode.VALIDATION_FAILED;        // REQUEST_VALIDATION_ERROR
            case 404 -> ResponseCode.RESOURCE_NOT_FOUND;
            case 429 -> ResponseCode.EXTERNAL_API_ERROR;       // OPENAI_RATE_LIMIT (전용 코드 없어 재사용)
            case 502 -> ResponseCode.EXTERNAL_API_ERROR;       // OPENAI_CONNECTION_ERROR / OPENAI_API_ERROR
            case 503 -> ResponseCode.SERVICE_UNAVAILABLE;      // RAG 저장소 준비 안 됨
            case 504 -> ResponseCode.EXTERNAL_API_TIMEOUT;     // OPENAI_TIMEOUT
            case 500 -> ResponseCode.FASTAPI_SERVER_ERROR;
            default -> ResponseCode.EXTERNAL_API_ERROR;
        };
    }
}