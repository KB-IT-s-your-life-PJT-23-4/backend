package com.example.project.admin.audit.service;

import com.example.project.admin.audit.domain.AdminAuditLogVO;
import com.example.project.admin.audit.support.InMemoryAdminAuditLogMapper;
import com.example.project.admin.auth.domain.AdminPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminAuditWriterTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("관리자 작업과 요청 메타데이터를 감사 로그로 저장한다")
    void recordAdminAction() throws Exception {
        InMemoryAdminAuditLogMapper mapper = new InMemoryAdminAuditLogMapper();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T03:00:00Z"),
                ZoneOffset.UTC
        );
        AdminAuditWriter writer = new AdminAuditWriter(mapper, objectMapper, clock);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH",
                "/api/admin/report/10"
        );
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "관리자 브라우저\r\n위조 헤더");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        writer.record(
                new AdminPrincipal(7L, "ROOT"),
                "REPORT_PROCESS",
                "SAFETY_REPORT",
                10L,
                "AI 안전 신고를 처리했습니다.",
                Map.of("status", "RESOLVED")
        );

        AdminAuditLogVO saved = mapper.getLogs().get(0);
        assertEquals(7L, saved.getActorUserId());
        assertEquals("ROOT", saved.getActorRole());
        assertEquals("REPORT_PROCESS", saved.getActionType());
        assertEquals("SAFETY_REPORT", saved.getTargetType());
        assertEquals("10", saved.getTargetId());
        assertEquals("PATCH", saved.getHttpMethod());
        assertEquals("/api/admin/report/10", saved.getRequestPath());
        assertEquals("127.0.0.1", saved.getIpAddress());
        assertEquals("관리자 브라우저  위조 헤더", saved.getUserAgent());
        assertNotNull(saved.getRequestId());
        assertEquals(clock.instant(), saved.getOccurredAt().toInstant(ZoneOffset.UTC));

        JsonNode changeData = objectMapper.readTree(saved.getChangeData());
        assertEquals("RESOLVED", changeData.get("status").asText());
    }
}
