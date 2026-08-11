package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.crypto.domain.EncryptedPayload;
import com.example.project.consultation.crypto.service.ConversationCryptoService;
import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.example.project.consultation.dto.response.ConversationHistoryResponse;
import com.example.project.consultation.dto.response.ConversationTurnResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import javax.inject.Qualifier;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationJsonService {

    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final ConversationCryptoService cryptoService;
    private final QuestionExcerptMasker questionExcerptMasker;

    private ConversationJsonService(
            @Qualifier("fastApiObjectMapper")
            ObjectMapper objectMapper,
            ConversationCryptoService cryptoService,
            QuestionExcerptMasker questionExcerptMasker
    ){
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.questionExcerptMasker = questionExcerptMasker;
    }

    public String createEmptyTranscript() {
        ObjectNode root = objectMapper.createObjectNode();

        root.put("schema_version", SCHEMA_VERSION);
        root.set("turns", objectMapper.createArrayNode());

        return writeJson(root);
    }

    public String appendProcessingTurn(
            String transcriptJson,
            Long userId,
            Long aiConversationId,
            String requestId,
            int turnNo,
            String turnType,
            String question,
            Object rawUserPayload,
            List<String> familyNames,
            LocalDateTime createdAt
    ){
        ObjectNode transcript = readObject(transcriptJson);

        ArrayNode turns = getTurns(transcript);

        String rawUserJson = writeJson(rawUserPayload);

        String userAad = buildAad(
                userId,
                aiConversationId,
                requestId,
                "USER"
        );

        EncryptedPayload encryptedUser = cryptoService.encrypt(rawUserJson, userAad);

        ObjectNode turn = objectMapper.createObjectNode();

        turn.put("request_id", requestId);
        turn.put("turn_no", turnNo);
        turn.put("turn_type", turnType);
        turn.put("status", "PROCESSING");
        turn.put("created_at", createdAt.toString());

        ObjectNode userNode = objectMapper.createObjectNode();

        /*
         * 운영 및 관리자 화면용 마스킹 문자열입니다.
         * 복원에는 사용하지 않습니다.
         */
        userNode.put(
                "masked_content",
                questionExcerptMasker.mask(question, familyNames)
            );

        /*
         * 사용자 이력 조회 시 복호화할 원본입니다.
         */
        userNode.set(
                "raw_encrypted",
                objectMapper.valueToTree(encryptedUser)
        );

        turn.set("user", userNode);
        turn.set(
                "assistant",
                objectMapper.nullNode()
        );

        turns.add(turn);

        return writeJson(transcript);
    }

    public String completeTurn(
            String transcriptJson,
            Long userId,
            Long aiConversationId,
            String requestId,
            ChatResponse response,
            LocalDateTime completedAt
    ) {
        ObjectNode transcript = readObject(transcriptJson);

        ObjectNode turn = findTurn(transcript, requestId);

        String rawResponseJson = writeJson(response);

        String assistantAad = buildAad(userId, aiConversationId, requestId, "ASSISTANT");

        EncryptedPayload encryptedResponse = cryptoService.encrypt(rawResponseJson, assistantAad);

        ObjectNode assistantNode = objectMapper.createObjectNode();

        //비민감 정보는 암호화하지 않고 저장
        assistantNode.put("conversation_id", response.conversationId());
        assistantNode.put("response_status", response.status().name());
        assistantNode.put("intent", response.intent());
        assistantNode.put("requires_calculation", response.requiresCalculation());

        //민감 정보는 암호화 후 저장
        assistantNode.set("raw_encrypted", objectMapper.valueToTree(encryptedResponse));

        turn.put("satus", "COMPLETED");
        turn.put("completed_at", completedAt.toString());
        turn.set("assistant", assistantNode);

        return writeJson(transcript);
    }

    public String failTurn(
            String transcriptJson,
            String requestId,
            LocalDateTime failedAt
    ) {
        ObjectNode transcript = readObject(transcriptJson);

        ObjectNode turn = findTurn(transcript, requestId);

        turn.put("status", "FAILED");
        turn.put("completed_at", failedAt.toString());

        return writeJson(transcript);
    }

    public ConversationHistoryResponse restoredHistory(
            Long userId,
            Long aiConversationId,
            String conversationId,
            String status,
            String transcriptJson
    ) {
        ObjectNode transcript = readObject(transcriptJson);

        ArrayNode turns = getTurns(transcript);

        List<ConversationTurnResponse> responses = new ArrayList<>();

        for (JsonNode node : turns) {
            String requestId = node.path("request_id").asText();
            int turnNo = node.path("turn_no").asInt();
            String turnType = node.path("turn_type").asText();
            String turnStatus = node.path("status").asText();
            LocalDateTime createdAt = parseDateTime(node.path("created_at"));
            LocalDateTime completedAt = parseDateTime(node.path("completed_at"));
            JsonNode encryptedUserNode = node.path("user").path("raw_encrypted");
            EncryptedPayload encryptedUser = objectMapper.convertValue(encryptedUserNode, EncryptedPayload.class);
            String rawUserJson = cryptoService.decrypt(
                    encryptedUser,
                    buildAad(userId, aiConversationId, requestId, "USER")
            );
            JsonNode rawUserPayload = readTree(rawUserJson);
            ChatResponse assistantResponse = null;
            JsonNode assistantNode = node.get("assistant");

            if (!assistantNode.isMissingNode() && !assistantNode.isNull()) {
                JsonNode encryptedAssistantNode = assistantNode.path("raw_encrypted");

                if (!encryptedAssistantNode.isMissingNode() && !encryptedAssistantNode.isNull()) {
                    EncryptedPayload encryptedAssistant = objectMapper.convertValue(encryptedAssistantNode, EncryptedPayload.class);

                    String rawAssistantJson = cryptoService.decrypt(
                            encryptedAssistant,
                            buildAad(userId, aiConversationId, requestId, "ASSISTANT")
                    );

                    assistantResponse = readChatResponse(rawAssistantJson);
                }
            }

            responses.add(
                    ConversationTurnResponse.builder()
                            .requestId(requestId)
                            .turnNo(turnNo)
                            .turnType(turnType)
                            .status(turnStatus)
                            .userPayload(rawUserPayload)
                            .assistantResponse(assistantResponse)
                            .createdAt(createdAt)
                            .completedAt(completedAt)
                            .build()
            );
        }

        return ConversationHistoryResponse.builder()
                .conversationId(conversationId)
                .status(status)
                .turns(responses)
                .build();
    }

    private ObjectNode findTurn(ObjectNode transcript, String requestId) {
        for (JsonNode node : getTurns(transcript)) {
            if(requestId.equals(node.path("request_id").asText())) {
                return (ObjectNode) node;
            }
        }

        throw new ServiceException(ResponseCode.DATABASE_ERROR);
    }

    private String buildAad(Long userId, Long aiConversationId, String requestId, String direction) {
        return userId + ":" + aiConversationId + ":" + requestId + ":" + direction;
    }

    private ArrayNode getTurns(ObjectNode transcript) {
        JsonNode turns = transcript.get("turns");

        if(turns == null || !turns.isArray()) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return (ArrayNode) turns;
    }

    private ObjectNode readObject(String json) {
        JsonNode node = readTree(json);

        if (!node.isObject()) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return (ObjectNode) node;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception){
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }

    private ChatResponse readChatResponse(String json) {
        try {
            return objectMapper.readValue(json, ChatResponse.class);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }

    private String writeJson(Object value){
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private LocalDateTime parseDateTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }

        return LocalDateTime.parse(node.asText());
    }
}
