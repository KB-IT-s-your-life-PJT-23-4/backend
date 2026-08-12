package com.example.project.admin.audit.service;

import com.example.project.admin.audit.domain.AdminAuditLogVO;
import com.example.project.admin.audit.mapper.AdminAuditLogMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditWriter {

    private final AdminAuditLogMapper adminAuditLogMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AdminAuditLogVO auditLog){
        int insertedRows = adminAuditLogMapper.insertAuditLog(auditLog);

        if (insertedRows != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }
}
