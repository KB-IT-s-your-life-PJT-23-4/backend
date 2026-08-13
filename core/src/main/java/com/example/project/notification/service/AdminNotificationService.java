package com.example.project.notification.service;

import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.notification.domain.AdminNotificationVO;
import com.example.project.notification.dto.response.AdminNotificationPageResponse;
import com.example.project.notification.mapper.AdminNotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private static final Set<String> NOTIFICATION_STATUSES = Set.of("OPEN", "RESOLVED");

    private static final Set<String> NOTIFICATION_TYPES =
            Set.of("BATCH_FAILURE", "SERVER_ERROR", "CLIENT_ERROR");

    private final AdminNotificationMapper adminNotificationMapper;

    /**
     * 알림 목록. 안 읽음 개수는 목록 필터와 무관하게 따로 센다. 필터 건 결과의 개수를 배지로 쓰면
     * 관리자가 화면에서 필터를 바꿀 때마다 배지 숫자가 흔들린다.
     */
    @Transactional(readOnly = true)
    public AdminNotificationPageResponse getPageNotificationList(
            int page,
            int size,
            String statusValue,
            String notificationTypeValue,
            boolean unreadOnly,
            long adminId
    ) {
        long offset = Pagination.calculateOffset(page, size);

        String status = normalizeFilter(statusValue, NOTIFICATION_STATUSES);
        String notificationType = normalizeFilter(notificationTypeValue, NOTIFICATION_TYPES);

        long totalElements = adminNotificationMapper.countNotifications(
                adminId,
                status,
                notificationType,
                unreadOnly
        );

        List<AdminNotificationVO> items = adminNotificationMapper.selectNotificationPage(
                adminId,
                status,
                notificationType,
                unreadOnly,
                offset,
                size
        );

        Pagination pagination = Pagination.of(page, size, totalElements, items.size());

        return AdminNotificationPageResponse.builder()
                .notifications(items)
                .unreadCount(adminNotificationMapper.countUnread(adminId))
                .pagination(pagination)
                .build();
    }

    @Transactional(readOnly = true)
    public long countUnread(long adminId) {
        return adminNotificationMapper.countUnread(adminId);
    }

    /**
     * 읽음 표시. 이미 읽은 알림을 다시 눌러도 성공으로 둔다(INSERT IGNORE 가 0 행을 돌려줌).
     * 없는 알림 ID 는 FK 위반으로 걸리므로 그때만 404 로 바꾼다.
     */
    @Transactional
    public void markRead(long adminNotificationId, long adminId) {
        requirePositive(adminNotificationId, adminId);

        try {
            adminNotificationMapper.insertRead(adminNotificationId, adminId);
        } catch (DataIntegrityViolationException exception) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
    }

    /**
     * 조치 완료 처리. 읽음과 달리 알림 하나에 한 번만 유효하다.
     * 0 행이면 없는 알림이거나 이미 다른 관리자가 처리한 것이라 충돌로 돌려준다.
     */
    @Transactional
    public void resolve(long adminNotificationId, long adminId) {
        requirePositive(adminNotificationId, adminId);

        if (adminNotificationMapper.updateResolved(adminNotificationId, adminId) == 0) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }
    }

    private void requirePositive(long adminNotificationId, long adminId) {
        if (adminNotificationId <= 0 || adminId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
    }

    private String normalizeFilter(String value, Set<String> allowedValues) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        if (!allowedValues.contains(normalized)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        return normalized;
    }
}
