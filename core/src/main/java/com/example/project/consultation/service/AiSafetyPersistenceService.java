package com.example.project.consultation.service;

import com.example.project.consultation.domain.AiSafetyReportVO;
import com.example.project.consultation.mapper.ConsultationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private final ConsultationMapper consultationMapper;

    @Transactional
    public void createReportIfRequired(
            Long userId,
            Long eventId,
            String intent,
            LocalDateTime occurredAt
    ) {
        if(INTENT_JAILBREAK.equalsIgnoreCase(intent)) {
            saveJailBreakReport(userId, eventId);

            return;
        }

        if (!INTENT_OTHER.equalsIgnoreCase(intent)){
            return;
        }

        AIOtherIntentCounter.CounterSnapshot counter = aiOtherIntentCounter.increment(
                userId,
                occurredAt
        );

        if(counter.getCount() < OTHER_REPORT_THRESHOLD) {
            return;
        }

        saveOtherThresholdReport(
                userId,
                eventId,
                counter,
                occurredAt.toLocalDate()
        );
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

}
