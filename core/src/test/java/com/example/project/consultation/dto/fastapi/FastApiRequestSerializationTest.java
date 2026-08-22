package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FastApiRequestSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 최초질문요청에는이전대화내역을포함하지않는다() throws Exception {
        ChatRequest request = new ChatRequest(
                null,
                "증여 계획을 알려주세요.",
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(request)
        );

        assertFalse(json.has("conversation_history"));
    }

    @Test
    void 추가답변요청에는이전대화내역을포함하지않는다() throws Exception {
        ClarificationRequest request = new ClarificationRequest(
                "conversation-id",
                "추가 답변입니다.",
                "assessment",
                true,
                Map.of(),
                Map.of("amount", 10_000_000L),
                List.of(),
                List.of(),
                List.of()
        );

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(request)
        );

        assertFalse(json.has("conversation_history"));
    }
}
