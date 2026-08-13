package com.example.project.admin.audit.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminAuditLogVO {
    private Long adminAuditLogId;

    private Long actorUserId;
    private String actorRole;

    private String actionType;
    private String targetType;
    private String targetId;
    private String actionSummary;

    private String changeData;
    private String result;

    private String requestId;
    private String httpMethod;
    private String requestPath;
    private String ipAddress;
    private String userAgent;

    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
