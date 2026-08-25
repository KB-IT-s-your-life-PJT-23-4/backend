package com.example.project.consultation.service;

import com.example.project.consultation.domain.AiConsultationEventVO;
import com.example.project.consultation.domain.AiConversationVO;
import com.example.project.consultation.domain.AiSafetyReportVO;
import com.example.project.consultation.domain.EtfVO;
import com.example.project.consultation.domain.FamilyPreviousGiftVO;
import com.example.project.consultation.domain.ProductVO;
import com.example.project.consultation.mapper.ConsultationMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSafetyPersistenceServiceTest {

    private static final Long USER_ID = 1L;

    @Test
    void jailbreak이면신고를즉시저장한다() {
        FakeConsultationMapper mapper = new FakeConsultationMapper();
        AiSafetyPersistenceService service = createService(mapper);

        service.createReportIfRequired(
                USER_ID,
                10L,
                "jailbreak",
                LocalDateTime.now()
        );

        assertEquals(1, mapper.reports.size());
        AiSafetyReportVO report = mapper.reports.get(0);
        assertEquals("JAILBREAK", report.getReportType());
        assertEquals("JAILBREAK:10", report.getReportKey());
        assertEquals(10L, report.getTriggerEventId());
        assertEquals(1, report.getOccurrenceCount());
    }

    @Test
    void other는열한번째에신고한다() {
        FakeConsultationMapper mapper = new FakeConsultationMapper();
        AiSafetyPersistenceService service = createService(mapper);
        LocalDateTime occurredAt = LocalDateTime.now();

        for (long eventId = 1L; eventId <= 10L; eventId++) {
            service.createReportIfRequired(
                    USER_ID,
                    eventId,
                    "other",
                    occurredAt
            );
        }

        assertTrue(mapper.reports.isEmpty());

        service.createReportIfRequired(
                USER_ID,
                11L,
                "other",
                occurredAt
        );

        assertEquals(1, mapper.reports.size());
        AiSafetyReportVO report = mapper.reports.get(0);
        assertEquals("OTHER_THRESHOLD", report.getReportType());
        assertEquals(11, report.getOccurrenceCount());
        assertNotNull(report.getCountWindowStartedAt());
        assertNotNull(report.getCountWindowEndedAt());
    }

    @Test
    void other신고후추가질문은누적범위의마지막시각과이벤트를갱신한다() {
        FakeConsultationMapper mapper = new FakeConsultationMapper();
        AiSafetyPersistenceService service = createService(mapper);
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 25, 10, 0);

        for (long eventId = 1L; eventId <= 11L; eventId++) {
            service.createReportIfRequired(
                    USER_ID,
                    eventId,
                    "other",
                    startedAt.plusMinutes(eventId - 1)
            );
        }

        LocalDateTime latestAt = startedAt.plusMinutes(11);
        service.createReportIfRequired(USER_ID, 12L, "other", latestAt);

        assertEquals(2, mapper.reports.size());
        AiSafetyReportVO latestReport = mapper.reports.get(1);
        assertEquals(12L, latestReport.getTriggerEventId());
        assertEquals(12, latestReport.getOccurrenceCount());
        assertEquals(startedAt, latestReport.getCountWindowStartedAt());
        assertEquals(latestAt, latestReport.getCountWindowEndedAt());
    }

    @Test
    void 안전대상이아닌분류는신고하지않는다() {
        FakeConsultationMapper mapper = new FakeConsultationMapper();
        AiSafetyPersistenceService service = createService(mapper);

        service.createReportIfRequired(
                USER_ID,
                1L,
                "concept",
                LocalDateTime.now()
        );

        assertTrue(mapper.reports.isEmpty());
    }

    private AiSafetyPersistenceService createService(
            FakeConsultationMapper mapper
    ) {
        return new AiSafetyPersistenceService(
                new AIOtherIntentCounter(),
                mapper
        );
    }

    private static final class FakeConsultationMapper
            implements ConsultationMapper {

        private final List<AiSafetyReportVO> reports = new ArrayList<>();

        @Override
        public List<FamilyPreviousGiftVO> selectAllByUserId(Long userId) {
            return List.of();
        }

        @Override
        public FamilyPreviousGiftVO selectByFamilyId(Long familyId, Long userId) {
            return null;
        }

        @Override
        public List<ProductVO> selectAllOnSaleProducts() {
            return List.of();
        }

        @Override
        public List<EtfVO> selectAllOnSaleEtfProducts() {
            return List.of();
        }

        @Override
        public Long lockUser(Long userId) {
            return userId;
        }

        @Override
        public AiConversationVO selectConversationForUpdate(
                Long aiConversationId,
                Long userId
        ) {
            return null;
        }

        @Override
        public AiConversationVO selectActiveConversation(Long userId) {
            return null;
        }

        @Override
        public int insertConversation(AiConversationVO conversation) {
            return 0;
        }

        @Override
        public int markConversationProcessing(
                Long aiConversationId,
                Long userId,
                String transcriptJson,
                LocalDateTime processingStartedAt
        ) {
            return 0;
        }

        @Override
        public int completeConversationTurn(
                Long aiConversationId,
                Long userId,
                String conversationId,
                String transcriptJson,
                LocalDateTime completedAt
        ) {
            return 0;
        }

        @Override
        public int failConversationTurn(
                Long aiConversationId,
                Long userId,
                String transcriptJson,
                LocalDateTime failedAt
        ) {
            return 0;
        }

        @Override
        public int insertAIConsultationEvent(AiConsultationEventVO event) {
            return 0;
        }

        @Override
        public int insertAIReport(AiSafetyReportVO report) {
            reports.add(report);
            return 1;
        }
    }
}
