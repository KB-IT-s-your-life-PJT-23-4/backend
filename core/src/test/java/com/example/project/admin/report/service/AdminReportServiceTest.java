package com.example.project.admin.report.service;

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

    @Test
    @DisplayName("신고 목록을 조회할 때 필터와 페이지 정보를 정규화해 매퍼에 전달한다")
    void getPageReportListNormalizesFiltersAndReturnsPagination() {
        ReportMapperStub mapper = new ReportMapperStub();
        mapper.totalElements = 45L;
        mapper.reports = List.of(report(101L), report(102L));
        AdminReportService service = new AdminReportService(mapper);

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
        AdminReportService service = new AdminReportService(mapper);

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
        AdminReportService service = new AdminReportService(mapper);

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
        AdminReportService service = new AdminReportService(mapper);

        service.processReport(
                10L,
                request("  resolved  ", "  정상적인 문의로 확인했습니다.  "),
                7L
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
        AdminReportService service = new AdminReportService(mapper);

        service.processReport(10L, request("IN_REVIEW", null), 7L);
        assertNull(mapper.observedResolutionNote);

        service.processReport(10L, request("OPEN", "   "), 7L);
        assertNull(mapper.observedResolutionNote);
        assertEquals(2, mapper.updateCalls);
    }

    @Test
    @DisplayName("신고 처리 요청값이 올바르지 않으면 저장하지 않는다")
    void processReportRejectsInvalidRequest() {
        ReportMapperStub mapper = new ReportMapperStub();
        AdminReportService service = new AdminReportService(mapper);

        assertBadRequest(() -> service.processReport(0L, request("OPEN", null), 7L));
        assertBadRequest(() -> service.processReport(10L, request("OPEN", null), 0L));
        assertBadRequest(() -> service.processReport(10L, null, 7L));
        assertBadRequest(() -> service.processReport(10L, request("", null), 7L));
        assertBadRequest(() -> service.processReport(10L, request("UNKNOWN", null), 7L));
        assertBadRequest(() -> service.processReport(10L, request("OPEN", "가".repeat(2001)), 7L));
        assertEquals(0, mapper.updateCalls);
    }

    @Test
    @DisplayName("수정할 신고가 없으면 찾을 수 없음 오류를 발생시킨다")
    void processReportRejectsMissingReport() {
        ReportMapperStub mapper = new ReportMapperStub();
        mapper.updatedRows = 0;
        AdminReportService service = new AdminReportService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.processReport(999L, request("RESOLVED", null), 7L)
        );

        assertEquals(ResponseCode.RESOURCE_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("신고 수정 결과가 한 건을 초과하면 데이터베이스 오류를 발생시킨다")
    void processReportRejectsUnexpectedAffectedRows() {
        ReportMapperStub mapper = new ReportMapperStub();
        mapper.updatedRows = 2;
        AdminReportService service = new AdminReportService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.processReport(10L, request("DISMISSED", null), 7L)
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
