package com.example.project.admin.audit.service;

import com.example.project.admin.audit.dto.response.AdminAuditLogPageResponse;
import com.example.project.admin.audit.dto.response.AdminAuditLogResponse;
import com.example.project.admin.audit.mapper.AdminAuditLogMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogMapper adminAuditLogMapper;

    @Transactional(readOnly = true)
    public AdminAuditLogPageResponse getAuditLogs(
            int page,
            int size,
            Long actorUserId,
            String actionType,
            String targetType,
            String targetId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        long offset = Pagination.calculateOffset(page, size);

        if (actorUserId != null && actorUserId <= 0){
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        if (from != null && to != null && !from.isBefore(to)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String normalizedActionType = normalizeCode(actionType);

        String normalizedTargetType = normalizeCode(targetType);

        String normalizedTargetId = normalizeTargetId(targetId);

        long totalElements = adminAuditLogMapper.countAuditLogs(
                actorUserId,
                normalizedActionType,
                normalizedTargetType,
                normalizedTargetId,
                from,
                to
        );

        List<AdminAuditLogResponse> items = adminAuditLogMapper.selectedAuditLogsPage(
                actorUserId,
                normalizedActionType,
                normalizedTargetType,
                normalizedTargetId,
                from,
                to,
                offset,
                size
        )
                        .stream()
                        .map(AdminAuditLogResponse::from)
                        .toList();

        return AdminAuditLogPageResponse.builder()
                .auditLogs(items)
                .pagination(
                        Pagination.of(page, size, totalElements, items.size())
                )
                .build();
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()){
            return null;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()){
            return null;
        }

        return targetId.trim();
    }
}
