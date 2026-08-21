//package com.example.project.consultation.client;
//
//import com.example.project.common.api.ResponseCode;
//import com.example.project.common.exception.ServiceException;
//import com.example.project.consultation.dto.fastapi.ChatRequest;
//import com.example.project.consultation.dto.fastapi.ChatResponse;
//import com.example.project.consultation.dto.fastapi.ChatStatus;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.web.client.HttpClientErrorException;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.Collections;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//
//class FastApiClientRestTemplateTest {
//
//    @Test
//    @DisplayName("상담 요청을 동기 POST로 전송하고 응답을 반환한다")
//    void postsChatSynchronously() {
//        ChatResponse expected = new ChatResponse(
//                null,
//                ChatStatus.COMPLETED,
//                null,
//                false,
//                null,
//                Collections.emptyList(),
//                Collections.emptyMap(),
//                Collections.emptyList()
//        );
//        StubRestTemplate restTemplate = new StubRestTemplate(expected, null);
//
//        FastApiClient client = new FastApiClient(restTemplate);
//        ChatResponse response = client.startChat(new ChatRequest(
//                null,
//                "증여 상담",
//                Collections.emptyList(),
//                Collections.emptyList(),
//                Collections.emptyList(),
//                Collections.emptyMap(),
//                Collections.emptyList()
//        ));
//
//        assertEquals(ChatStatus.COMPLETED, response.status());
//        assertEquals("/api/v1/chat", restTemplate.requestUri);
//    }
//
//    @Test
//    @DisplayName("FastAPI의 422 응답을 검증 실패로 변환한다")
//    void mapsValidationFailure() {
//        HttpClientErrorException failure = HttpClientErrorException.create(
//                HttpStatus.UNPROCESSABLE_ENTITY,
//                "Unprocessable Entity",
//                HttpHeaders.EMPTY,
//                null,
//                null
//        );
//        RestTemplate restTemplate = new StubRestTemplate(null, failure);
//
//        FastApiClient client = new FastApiClient(restTemplate);
//        ServiceException exception = assertThrows(
//                ServiceException.class,
//                () -> client.startChat(null)
//        );
//
//        assertEquals(ResponseCode.VALIDATION_FAILED, exception.getResponseCode());
//    }
//
//    private static class StubRestTemplate extends RestTemplate {
//
//        private final ChatResponse response;
//        private final RuntimeException failure;
//        private String requestUri;
//
//        private StubRestTemplate(
//                ChatResponse response,
//                RuntimeException failure
//        ) {
//            this.response = response;
//            this.failure = failure;
//        }
//
//        @Override
//        public <T> T postForObject(
//                String url,
//                Object request,
//                Class<T> responseType,
//                Object... uriVariables
//        ) {
//            requestUri = url;
//            if (failure != null) {
//                throw failure;
//            }
//            return responseType.cast(response);
//        }
//    }
//}
