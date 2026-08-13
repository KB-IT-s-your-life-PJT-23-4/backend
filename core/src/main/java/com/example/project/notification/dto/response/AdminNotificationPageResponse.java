package com.example.project.notification.dto.response;

import com.example.project.common.api.Pagination;
import com.example.project.notification.domain.AdminNotificationVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class AdminNotificationPageResponse {

    private final List<AdminNotificationVO> notifications;

    private final long unreadCount;

    private final Pagination pagination;
}
