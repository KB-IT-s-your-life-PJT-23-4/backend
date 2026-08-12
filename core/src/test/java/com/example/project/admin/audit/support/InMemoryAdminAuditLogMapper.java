package com.example.project.admin.audit.support;

import com.example.project.admin.audit.domain.AdminAuditLogVO;
import com.example.project.admin.audit.mapper.AdminAuditLogMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InMemoryAdminAuditLogMapper implements AdminAuditLogMapper {

    private final List<AdminAuditLogVO> logs = new ArrayList<>();

    @Override
    public int insertAuditLog(AdminAuditLogVO adminLog) {
        adminLog.setAdminAuditLogId((long) logs.size() + 1);
        logs.add(adminLog);
        return 1;
    }

    @Override
    public long countAuditLogs(
            Long actorUserId,
            String actionType,
            String targetType,
            String targetId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return logs.size();
    }

    @Override
    public List<AdminAuditLogVO> selectedAuditLogsPage(
            Long actorUserId,
            String actionType,
            String targetType,
            String targetId,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int size
    ) {
        int start = (int) Math.min(offset, logs.size());
        int end = Math.min(start + size, logs.size());
        return List.copyOf(logs.subList(start, end));
    }

    public List<AdminAuditLogVO> getLogs() {
        return List.copyOf(logs);
    }
}
