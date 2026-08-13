package com.example.project.admin.audit.dto.response;

import com.example.project.common.api.Pagination;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogPageResponse {

    private List<AdminAuditLogResponse> auditLogs;
    private Pagination pagination;
}
