package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.AiConsultationEventVO;
import com.example.project.consultation.domain.AiConversationVO;
import com.example.project.consultation.domain.ConversationContextSnapshot;
import com.example.project.consultation.domain.PreparedConversation;
import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.example.project.consultation.dto.response.ConversationHistoryResponse;
import com.example.project.consultation.mapper.ConsultationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationPersistenceService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String PROCESSING_IDLE = "IDLE";
    private static final String PROCESSING = "PROCESSING";
    private static final Duration STALE_PROCESSING_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_CONTEXT_TURNS = 10;

    private final ConsultationMapper consultationMapper;
    private final ConversationJsonService conversationJsonService;
    private final QuestionExcerptMasker questionExcerptMasker;
    private final AiSafetyPersistenceService aiSafetyPersistenceService;
    private final Clock applicationClock;

    @Transactional
    public PreparedConversation prepareTurn(
            Long userId,
            String turnType,
            String question,
            Object rawUserPayload,
            List<String> familyNames
    ) {
        if(userId == null){
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        Long lockedUserId = consultationMapper.lockUser(userId);

        if (lockedUserId == null) {
            throw new ServiceException(ResponseCode.MEMBER_NOT_FOUND);
        }

        AiConversationVO conversation = consultationMapper
                .selectActiveConversation(userId);

        if (conversation == null) {
            conversation = createConversation(userId);
        }

        LocalDateTime now = LocalDateTime.now(applicationClock);

        if (PROCESSING.equals(conversation.getProcessingStatus())) {
            conversation = recoverStaleProcessing(conversation, userId, now);
        }

        if (!PROCESSING_IDLE.equals(conversation.getProcessingStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        ConversationContextSnapshot contextSnapshot =
                conversationJsonService.restoreContext(
                        userId,
                        conversation.getAiConversationId(),
                        conversation.getConversationId(),
                        conversation.getStatus(),
                        conversation.getTranscriptJson(),
                        MAX_CONTEXT_TURNS
                );

        String requestId = UUID.randomUUID().toString();

        int turnNo = conversation.getTurnCount() + 1;

        String updatedTranscript = conversationJsonService.appendProcessingTurn(
                conversation.getTranscriptJson(),
                userId,
                conversation.getAiConversationId(),
                requestId,
                turnNo,
                turnType,
                question,
                rawUserPayload,
                familyNames,
                now
        );

        int updatedCount = consultationMapper.markConversationProcessing(
                conversation.getAiConversationId(),
                userId,
                updatedTranscript,
                now
        );

        if (updatedCount != 1) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        return PreparedConversation.builder()
                .aiConversationId(conversation.getAiConversationId())
                .userId(userId)
                .conversationId(conversation.getConversationId())
                .requestId(requestId)
                .turnNo(turnNo)
                .questionExcerpt(
                        questionExcerptMasker.mask(question, familyNames)
                )
                .conversationHistory(contextSnapshot.getMessages())
                .facts(contextSnapshot.getFacts())
                .build();
    }

    @Transactional
    public void completeTurn(PreparedConversation prepared, ChatResponse response) {
        validateResponse(response);

        AiConversationVO conversation = consultationMapper.selectConversationForUpdate(
                prepared.getAiConversationId(), prepared.getUserId()
        );

        if (conversation == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }

        if (!PROCESSING.equals(conversation.getProcessingStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        if (conversation.getConversationId() != null
        && !conversation.getConversationId().equals(response.conversationId())) {
            throw new ServiceException(ResponseCode.EXTERNAL_API_ERROR);
        }

        LocalDateTime now = LocalDateTime.now(applicationClock);

        String updatedTranscript = conversationJsonService.completeTurn(
                conversation.getTranscriptJson(),
                prepared.getUserId(),
                conversation.getAiConversationId(),
                prepared.getRequestId(),
                response,
                now
        );

        int updatedCount = consultationMapper.completeConversationTurn(
                conversation.getAiConversationId(),
                prepared.getUserId(),
                response.conversationId(),
                updatedTranscript,
                now
        );

        if (updatedCount != 1) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        AiConsultationEventVO event = AiConsultationEventVO.builder()
                .aiConversationId(conversation.getAiConversationId())
                .conversationId(response.conversationId())
                .requestId(prepared.getRequestId())
                .turnNo(prepared.getTurnNo())
                .userId(prepared.getUserId())
                .intent(response.intent())
                .responseStatus(response.status().name())
                .questionExcerpt(prepared.getQuestionExcerpt())
                .occurredAt(now)
                .build();

        int insertedEventCount = consultationMapper.insertAIConsultationEvent(event);

        if (insertedEventCount != 1 || event.getAiConsultationEventId() == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        aiSafetyPersistenceService.createReportIfRequired(
                prepared.getUserId(),
                event.getAiConsultationEventId(),
                response.intent(),
                now
        );

    }

    @Transactional
    public void failTurn(PreparedConversation prepared){
        AiConversationVO conversation = consultationMapper.selectConversationForUpdate(
                prepared.getAiConversationId(), prepared.getUserId()
        );

        if (conversation == null) {
            return;
        }

        if (!PROCESSING.equals(conversation.getProcessingStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(applicationClock);

        String updatedTranscript = conversationJsonService.failTurn(
                        conversation.getTranscriptJson(),
                        prepared.getRequestId(),
                        now
                );

        int updatedCount = consultationMapper.failConversationTurn(
                        conversation.getAiConversationId(),
                        prepared.getUserId(),
                        updatedTranscript,
                        now
                );

        if (updatedCount != 1) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponse getCurrentHistory(Long userId) {
        AiConversationVO conversation = consultationMapper.selectActiveConversation(userId);

        if (conversation == null) {
            /*
             * 아직 질문하지 않은 사용자는 빈 이력을 반환합니다.
             */
            return ConversationHistoryResponse.builder()
                    .conversationId(null)
                    .status(null)
                    .turns(List.of())
                    .build();
        }

        /*
         * 반드시 인증된 userId로 조회한 세션만 복호화합니다.
         */
        return conversationJsonService.restoreHistory(
                userId,
                conversation.getAiConversationId(),
                conversation.getConversationId(),
                conversation.getStatus(),
                conversation.getTranscriptJson()
        );
    }

    private AiConversationVO createConversation(Long userId) {
        AiConversationVO conversation = AiConversationVO.builder()
                .conversationId(null)
                .userId(userId)
                .status(STATUS_ACTIVE)
                .processingStatus(PROCESSING_IDLE)
                .transcriptJson(conversationJsonService.createEmptyTranscript())
                .turnCount(0)
                .build();

        int insertCount = consultationMapper.insertConversation(conversation);

        if (insertCount != 1 || conversation.getAiConversationId() == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return conversation;
    }

    private AiConversationVO recoverStaleProcessing(
            AiConversationVO conversation,
            Long userId,
            LocalDateTime now
    ) {
        LocalDateTime startedAt = conversation.getProcessingStartedAt();

        if (startedAt != null
                && startedAt.plus(STALE_PROCESSING_TIMEOUT).isAfter(now)) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        String requestId = conversationJsonService.findLastProcessingRequestId(
                conversation.getTranscriptJson()
        );

        String recoveredTranscript = conversationJsonService.failTurn(
                conversation.getTranscriptJson(),
                requestId,
                now
        );

        int updatedCount = consultationMapper.failConversationTurn(
                conversation.getAiConversationId(),
                userId,
                recoveredTranscript,
                now
        );

        if (updatedCount != 1) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        conversation.setTranscriptJson(recoveredTranscript);
        conversation.setProcessingStatus(PROCESSING_IDLE);
        conversation.setProcessingStartedAt(null);

        return conversation;
    }

    private void validateResponse(ChatResponse response) {
        if (response == null
            || response.conversationId() == null
            || response.conversationId().isBlank()
            || response.status() == null
            || response.intent() == null
            || response.intent().isBlank()){
            throw new ServiceException(ResponseCode.EXTERNAL_API_ERROR);
        }
    }
}
