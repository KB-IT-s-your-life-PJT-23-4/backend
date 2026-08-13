package com.example.project.notification.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class AdminNotificationVO {

    private Long adminNotificationId;
    private String notificationType;
    private String severity;
    private String title;
    private String message;

    /**
     * 배치 실패는 'BATCH_JOB_EXECUTION' + 실행 ID. 상세 화면에서 배치 메타테이블로 이어진다.
     */
    private String referenceType;
    private Long referenceId;

    private String dedupeKey;
    private Integer occurrenceCount;
    private LocalDateTime lastOccurredAt;

    private String status;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalTime createdAt;

    private boolean read;
}
