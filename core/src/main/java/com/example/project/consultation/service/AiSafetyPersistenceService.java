package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.AiConsultationEventVO;
import com.example.project.consultation.domain.AiSafetyReportVO;
import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.example.project.consultation.mapper.ConsultationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiSafetyPersistenceService {

    private static final int OTHER_REPORT_THRESHOLD = 11;
    private static final String INTENT_JAILBREAK = "jailbreak";
    private static final String INTENT_OTHER = "other";

    private static final String REPORT_TYPE_JAILBREAK =
            "JAILBREAK";

    private static final String REPORT_TYPE_OTHER_THRESHOLD =
            "OTHER_THRESHOLD";

    private static final String REPORT_STATUS_OPEN =
            "OPEN";


    private final AIOtherIntentCounter aiOtherIntentCounter;
    private final QuestionExcerptMasker questionExcerptMasker;
    private final ConsultationMapper consultationMapper;

    @Transactional
    public void process(Long userId, String question, List<String> familyNames, ChatResponse response) {

        validateArguments(userId, response);

        String intent = response.intent();

        if(!isSafetyIntent(intent)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        AiConsultationEventVO event = createEvent(userId, question, familyNames, response, now);

        int insertedEventCount = consultationMapper.insertAIConsultationEvent(event);

        if(insertedEventCount != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
        Long eventId = event.getAiConsultationEventId();

        if (eventId == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        if(INTENT_JAILBREAK.equalsIgnoreCase(intent)) {
            saveJailBreakReport(userId, eventId);

            return;
        }

        AIOtherIntentCounter.CounterSnapshot counter = aiOtherIntentCounter.increment(userId);

        if(counter.getCount() >= OTHER_REPORT_THRESHOLD) {
            saveOtherThresholdReport(
                    userId,
                    eventId,
                    counter,
                    now.toLocalDate()
            );
        }
    }

    private AiConsultationEventVO createEvent(
            Long userId,
            String question,
            List<String> familyNames,
            ChatResponse response,
            LocalDateTime occurredAt
    ) {
        String maskedExcerpt = questionExcerptMasker.mask(question, familyNames);

        return AiConsultationEventVO.builder()
                .userId(userId)
                .conversationId(response.conversationId())
                .intent(response.intent())
                .responseStatus(response.status().name())
                .questionExcerpt(maskedExcerpt)
                .occurredAt(occurredAt)
                .build();
    }

    private void saveJailBreakReport(Long userId, Long eventId) {
        AiSafetyReportVO report = AiSafetyReportVO.builder()
                .reportKey(REPORT_TYPE_JAILBREAK+":"+eventId)
                .reportType(REPORT_TYPE_JAILBREAK)
                .status(REPORT_STATUS_OPEN)
                .userId(userId)
                .triggerEventId(eventId)
                .occurrenceCount(1)
                .build();

        consultationMapper.insertAIReport(report);
    }

    private void saveOtherThresholdReport(
            Long userId,
            Long eventId,
            AIOtherIntentCounter.CounterSnapshot counter,
            LocalDate reportDate
    ) {
        LocalDateTime windowsStartedAt = reportDate.atStartOfDay();

        LocalDateTime windowEndedAt = reportDate.plusDays(1).atStartOfDay();

        String reportKey = REPORT_TYPE_OTHER_THRESHOLD + ":" + userId + ":" + reportDate.format(DateTimeFormatter.BASIC_ISO_DATE);

        AiSafetyReportVO report = AiSafetyReportVO.builder()
                .reportKey(reportKey)
                .reportType(REPORT_TYPE_OTHER_THRESHOLD)
                .status(REPORT_STATUS_OPEN)
                .userId(userId)
                .triggerEventId(eventId)
                .occurrenceCount(counter.getCount())
                .countWindowStartedAt(counter.getFirstDetectedAt())
                .countWindowEndedAt(counter.getThresholdReachedAt())
                .build();

        consultationMapper.insertAIReport(report);
    }

    private boolean isSafetyIntent(String intent) {
        return INTENT_JAILBREAK.equalsIgnoreCase(intent) || INTENT_OTHER.equalsIgnoreCase(intent);
    }

    private void validateArguments(Long userId, ChatResponse response){
        if (userId == null){
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        if(response == null || response.intent() == null || response.status() == null) {
            throw new ServiceException(ResponseCode.EXTERNAL_API_ERROR);
        }
    }
}
