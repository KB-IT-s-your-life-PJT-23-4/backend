package com.example.project.admin.audit.dto.response;

import com.example.project.admin.audit.domain.AdminAuditLogVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogResponse {

    private Long auditLogId;
    private Long actorUserId;
    private String actorRole;

    private String actionType;
    private String targetType;
    private String targetId;
    private String actionSummary;

    private String changeData;
    private String result;

    private String ipAddress;
    private LocalDateTime occurredAt;

    public static AdminAuditLogResponse from(AdminAuditLogVO adminAuditLogVO) {

        return AdminAuditLogResponse.builder()
                .auditLogId(adminAuditLogVO.getAdminAuditLogId())
                .actorUserId(adminAuditLogVO.getActorUserId())
                .actorRole(adminAuditLogVO.getActorRole())
                .actionType(adminAuditLogVO.getActionType())
                .targetType(adminAuditLogVO.getTargetType())
                .targetId(adminAuditLogVO.getTargetId())
                .actionSummary(adminAuditLogVO.getActionSummary())
                .changeData(adminAuditLogVO.getChangeData())
                .result(adminAuditLogVO.getResult())
                .ipAddress(adminAuditLogVO.getIpAddress())
                .occurredAt(adminAuditLogVO.getOccurredAt())
                .build();
    }
}
