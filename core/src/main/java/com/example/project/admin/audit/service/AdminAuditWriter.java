package com.example.project.admin.audit.service;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.audit.domain.AdminAuditLogVO;
import com.example.project.admin.audit.mapper.AdminAuditLogMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminAuditWriter {

    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final AdminAuditLogMapper adminAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    @Autowired
    public AdminAuditWriter(
            AdminAuditLogMapper adminAuditLogMapper,
            @Qualifier("fastApiObjectMapper") ObjectMapper objectMapper,
            Clock applicationClock
    ) {
        this.adminAuditLogMapper = adminAuditLogMapper;
        this.objectMapper = objectMapper;
        this.applicationClock = applicationClock;
    }

    public AdminAuditWriter(AdminAuditLogMapper adminAuditLogMapper) {
        this(
                adminAuditLogMapper,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.systemDefaultZone()
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            AdminPrincipal actor,
            String actionType,
            String targetType,
            Object targetId,
            String actionSummary,
            Map<String, Object> changeData
    ) {
        if (actor == null
                || actor.userId() == null
                || actor.role() == null
                || actionType == null
                || actionType.isBlank()
                || targetType == null
                || targetType.isBlank()
                || actionSummary == null
                || actionSummary.isBlank()) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        HttpServletRequest request = currentRequest();
        AdminAuditLogVO auditLog = AdminAuditLogVO.builder()
                .actorUserId(actor.userId())
                .actorRole(actor.role())
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId == null ? null : String.valueOf(targetId))
                .actionSummary(actionSummary)
                .changeData(serializeChangeData(changeData))
                .result("SUCCESS")
                .requestId(UUID.randomUUID().toString())
                .httpMethod(request == null ? null : request.getMethod())
                .requestPath(request == null ? null : request.getRequestURI())
                .ipAddress(request == null ? null : request.getRemoteAddr())
                .userAgent(request == null ? null : sanitizeUserAgent(request.getHeader("User-Agent")))
                .occurredAt(LocalDateTime.now(applicationClock))
                .build();

        int insertedRows = adminAuditLogMapper.insertAuditLog(auditLog);

        if (insertedRows != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private String serializeChangeData(Map<String, Object> changeData) {
        if (changeData == null || changeData.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(changeData);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        String sanitized = userAgent
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();

        return sanitized.length() <= MAX_USER_AGENT_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
