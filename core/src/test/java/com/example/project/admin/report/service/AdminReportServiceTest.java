package com.example.project.admin.report.service;

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

    private static class ReportMapperStub implements ReportMapper {

        private long totalElements;
        private List<AiSafetyReportVO> reports = List.of();
        private String observedStatus;
        private String observedReportType;
        private long observedOffset;
        private int observedSize;
        private int countCalls;
        private int selectCalls;

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
    }
}
