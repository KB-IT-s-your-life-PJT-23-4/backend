package com.example.project.consultation.dto.response;

import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurnResponse {
    private String requestId;
    private Integer turnNo;
    private String turnType;
    private String status;

    /*
     * 복호화한 사용자 요청입니다.
     *
     * 최초 질문:
     * { "question": "..." }
     *
     * 추가 정보 응답:
     * {
     *   "question": "...",
     *   "intent": "...",
     *   "facts": {},
     *   "answers": {}
     * }
     */
    private JsonNode userPayload;
    private ChatResponse assistantResponse;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
