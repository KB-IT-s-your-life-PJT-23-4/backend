package com.example.project.admin.report.service;

import com.example.project.admin.audit.service.AdminAuditWriter;
import com.example.project.admin.audit.support.InMemoryAdminAuditLogMapper;
import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.report.domain.AdminReportQuestionRow;
import com.example.project.admin.report.dto.request.ReportProcessRequest;
import com.example.project.admin.report.dto.response.AdminReportPageResponse;
import com.example.project.admin.report.mapper.ReportMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.AiSafetyReportVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminReportServiceTest {

    private static final AdminPrincipal ACTOR = new AdminPrincipal(7L, "MIDDLE");

    @Test
    @DisplayName("신고 목록을 조회할 때 필터와 페이지 정보를 정규화해 매퍼에 전달한다")
    void getPageReportListNormalizesFiltersAndReturnsPagination() {
        ReportMapperStub mapper = new ReportMapperStub();
        mapper.totalElements = 45L;
        mapper.reports = List.of(report(101L), report(102L));
        mapper.questionRows = List.of(
                new AdminReportQuestionRow(101L, "탈옥 시도 질문"),
                new AdminReportQuestionRow(102L, "반복 질문 1"),
                new AdminReportQuestionRow(102L, "반복 질문 2")
        );
        AdminReportService service = service(mapper);

        AdminReportPageResponse response = service.getPageReportList(
                1,
                20,
                "  open  ",
                "  jailbreak  "
        );

        assertEquals("OPEN", mapper.observedStatus);
        assertEquals("JAILBREAK", mapper.observedReportType);
        assertEquals(20L, mapper.observedOffset);
        assertEquals(20, mapper.observedSize);
        assertSame(mapper.reports, response.getReports());
        assertEquals(List.of("탈옥 시도 질문"),
                response.getReports().get(0).getQuestionExcerpts());
        assertEquals(List.of("반복 질문 1", "반복 질문 2"),
                response.getReports().get(1).getQuestionExcerpts());

        Pagination pagination = response.getPagination();
        assertEquals(1, pagination.getPage());
        assertEquals(20, pagination.getSize());
        assertEquals(45L, pagination.getTotalElements());
        assertEquals(3, pagination.getTotalPages());
        assertEquals(2, pagination.getNumberOfElements());
        assertFalse(pagination.isFirst());
        assertFalse(pagination.isLast());
        assertTrue(pagination.isHasNext());
        assertTrue(pagination.isHasPrevious());
    }

    @Test
    @DisplayName("빈 필터는 전체 조회 조건으로 처리한다")
    void getPageReportListTreatsBlankFiltersAsNull() {
        ReportMapperStub mapper = new ReportMapperStub();
        AdminReportService service = service(mapper);

        AdminReportPageResponse response = service.getPageReportList(
                0,
                20,
                "   ",
                null
        );

        assertNull(mapper.observedStatus);
        assertNull(mapper.observedReportType);
        assertTrue(response.getReports().isEmpty());
        assertTrue(response.getPagination().isFirst());
        assertTrue(response.getPagination().isLast());
        assertFalse(response.getPagination().isHasNext());
        assertFalse(response.getPagination().isHasPrevious());
    }

    @Test
    @DisplayName("페이지 범위와 신고 필터가 올바르지 않으면 요청 오류를 발생시킨다")
    void getPageReportListRejectsInvalidRequest() {
        ReportMapperStub mapper = new ReportMapperStub();
        AdminReportService service = service(mapper);

        assertBadRequest(() -> service.getPageReportList(-1, 20, null, null));
        assertBadRequest(() -> service.getPageReportList(0, 0, null, null));
        assertBadRequest(() -> service.getPageReportList(0, 101, null, null));
        assertBadRequest(() -> service.getPageReportList(0, 20, "UNKNOWN", null));
        assertBadRequest(() -> service.getPageReportList(0, 20, null, "UNKNOWN"));
        assertEquals(0, mapper.countCalls);
        assertEquals(0, mapper.selectCalls);
    }

    @Test
    @DisplayName("신고 처리 상태와 관리자 처리 내용을 정규화해 저장한다")
    void processReportNormalizesAndUpdatesReport() {
        ReportMapperStub mapper = new ReportMapperStub();
        AdminReportService service = service(mapper);

        service.processReport(
                10L,
                request("  resolved  ", "  정상적인 문의로 확인했습니다.  "),
                ACTOR
        );

        assertEquals(10L, mapper.observedReportId);
        assertEquals("RESOLVED", mapper.observedUpdateStatus);
        assertEquals(7L, mapper.observedAdminId);
        assertEquals("정상적인 문의로 확인했습니다.", mapper.observedResolutionNote);
        assertEquals(1, mapper.updateCalls);
    }

    @Test
    @DisplayName("관리자 처리 내용은 null 또는 빈 문자열일 수 있다")
    void processReportAllowsNullableResolutionNote() {
        ReportMapperStub mapper = new ReportMapperStub();
        AdminReportService service = service(mapper);

        service.processReport(10L, request("IN_REVIEW", null), ACTOR);
        assertNull(mapper.observedResolutionNote);

        service.processReport(10L, request("OPEN", "   "), ACTOR);
        assertNull(mapper.observedResolutionNote);
        assertEquals(2, mapper.updateCalls);
    }

    @Test
    @DisplayName("신고 처리 요청값이 올바르지 않으면 저장하지 않는다")
    void processReportRejectsInvalidRequest() {
        ReportMapperStub mapper = new ReportMapperStub();
        AdminReportService service = service(mapper);

        assertBadRequest(() -> service.processReport(0L, request("OPEN", null), ACTOR));
        assertBadRequest(() -> service.processReport(
                10L,
                request("OPEN", null),
                new AdminPrincipal(0L, "MIDDLE")
        ));
        assertBadRequest(() -> service.processReport(10L, null, ACTOR));
        assertBadRequest(() -> service.processReport(10L, request("", null), ACTOR));
        assertBadRequest(() -> service.processReport(10L, request("UNKNOWN", null), ACTOR));
        assertBadRequest(() -> service.processReport(10L, request("OPEN", "가".repeat(2001)), ACTOR));
        assertEquals(0, mapper.updateCalls);
    }

    @Test
    @DisplayName("수정할 신고가 없으면 찾을 수 없음 오류를 발생시킨다")
    void processReportRejectsMissingReport() {
        ReportMapperStub mapper = new ReportMapperStub();
        mapper.updatedRows = 0;
        AdminReportService service = service(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.processReport(999L, request("RESOLVED", null), ACTOR)
        );

        assertEquals(ResponseCode.RESOURCE_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("신고 수정 결과가 한 건을 초과하면 데이터베이스 오류를 발생시킨다")
    void processReportRejectsUnexpectedAffectedRows() {
        ReportMapperStub mapper = new ReportMapperStub();
        mapper.updatedRows = 2;
        AdminReportService service = service(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.processReport(10L, request("DISMISSED", null), ACTOR)
        );

        assertEquals(ResponseCode.DATABASE_ERROR, exception.getResponseCode());
    }

    private void assertBadRequest(Executable executable) {
        ServiceException exception = assertThrows(
                ServiceException.class,
                executable
        );
        assertEquals(ResponseCode.BAD_REQUEST, exception.getResponseCode());
    }

    private AdminReportService service(ReportMapper mapper) {
        return new AdminReportService(
                mapper,
                new AdminAuditWriter(new InMemoryAdminAuditLogMapper())
        );
    }

    private AiSafetyReportVO report(Long reportId) {
        return AiSafetyReportVO.builder()
                .aiSafetyReportId(reportId)
                .reportType("JAILBREAK")
                .status("OPEN")
                .build();
    }

    private ReportProcessRequest request(String status, String resolutionNote) {
        return ReportProcessRequest.builder()
                .status(status)
                .resolutionNote(resolutionNote)
                .build();
    }

    private static class ReportMapperStub implements ReportMapper {

        private long totalElements;
        private List<AiSafetyReportVO> reports = List.of();
        private List<AdminReportQuestionRow> questionRows = List.of();
        private String observedStatus;
        private String observedReportType;
        private long observedOffset;
        private int observedSize;
        private int countCalls;
        private int selectCalls;
        private int updateCalls;
        private int updatedRows = 1;
        private long observedReportId;
        private String observedUpdateStatus;
        private long observedAdminId;
        private String observedResolutionNote;

        @Override
        public List<AiSafetyReportVO> selectAiReportsPage(
                String status,
                String reportType,
                long offset,
                int size
        ) {
            selectCalls++;
            observedStatus = status;
            observedReportType = reportType;
            observedOffset = offset;
            observedSize = size;
            return reports;
        }

        @Override
        public long countAiReports(String status, String reportType) {
            countCalls++;
            observedStatus = status;
            observedReportType = reportType;
            return totalElements;
        }

        @Override
        public List<AdminReportQuestionRow> selectReportQuestionExcerpts(
                List<Long> reportIds
        ) {
            return questionRows;
        }

        @Override
        public int updateReport(
                long reportId,
                String status,
                long adminId,
                String resolutionNote
        ) {
            updateCalls++;
            observedReportId = reportId;
            observedUpdateStatus = status;
            observedAdminId = adminId;
            observedResolutionNote = resolutionNote;
            return updatedRows;
        }
    }
}
