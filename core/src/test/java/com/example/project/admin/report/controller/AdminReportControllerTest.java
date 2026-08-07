package com.example.project.admin.report.controller;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.report.mapper.ReportMapper;
import com.example.project.admin.report.service.AdminReportService;
import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.consultation.domain.AiSafetyReportVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminReportControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ReportMapperStub reportMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        reportMapper = new ReportMapperStub();
        AdminReportController controller = new AdminReportController(
                new AdminReportService(reportMapper)
        );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();

        AdminPrincipal principal = new AdminPrincipal(7L, "ROOT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ROOT"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("관리자가 신고 상태와 nullable 처리 내용을 수정한다")
    void processReport() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/report/{reportId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "resolved",
                                  "resolutionNote": null
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(203, body.get("statusCode").asInt());
        assertEquals("/api/admin/report/10", body.get("path").asText());
        assertEquals(10L, reportMapper.reportId);
        assertEquals("RESOLVED", reportMapper.reportStatus);
        assertEquals(7L, reportMapper.adminId);
        assertNull(reportMapper.resolutionNote);
    }

    @Test
    @DisplayName("신고 처리 상태가 비어 있으면 검증 오류를 반환한다")
    void rejectBlankStatus() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/report/{reportId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "",
                                  "resolutionNote": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(407, body(result).get("statusCode").asInt());
        assertEquals(0, reportMapper.updateCalls);
    }

    @Test
    @DisplayName("허용되지 않은 신고 처리 상태는 요청 오류를 반환한다")
    void rejectUnknownStatus() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/report/{reportId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(400, body(result).get("statusCode").asInt());
        assertEquals(0, reportMapper.updateCalls);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private static class ReportMapperStub implements ReportMapper {

        private int updateCalls;
        private long reportId;
        private String reportStatus;
        private long adminId;
        private String resolutionNote;

        @Override
        public List<AiSafetyReportVO> selectAiReportsPage(
                String status,
                String reportType,
                long offset,
                int size
        ) {
            return List.of();
        }

        @Override
        public long countAiReports(String status, String reportType) {
            return 0;
        }

        @Override
        public int updateReport(
                long reportId,
                String status,
                long adminId,
                String resolutionNote
        ) {
            updateCalls++;
            this.reportId = reportId;
            this.reportStatus = status;
            this.adminId = adminId;
            this.resolutionNote = resolutionNote;
            return 1;
        }
    }
}
