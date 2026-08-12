package com.example.project.admin.audit.mapper;

import com.example.project.admin.audit.domain.AdminAuditLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminAuditLogMapper {

    int insertAuditLog(AdminAuditLogVO adminLog);

    long countAuditLogs(
            @Param("actorUserId") Long actorUserId,
            @Param("actionType") String actionType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("from")LocalDateTime from,
            @Param("to") LocalDateTime to
            );

    List<AdminAuditLogVO> selectedAuditLogsPage(
            @Param("actorUserId") Long actorUserId,
            @Param("actionType") String actionType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") long offset,
            @Param("size") int size
    );
}
