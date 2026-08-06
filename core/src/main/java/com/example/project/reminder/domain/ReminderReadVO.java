package com.example.project.reminder.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 읽음 기록 한 건. 리마인더 본문은 저장하지 않으므로 이 테이블에서 필요한 건 "언제 읽었나"뿐이다.
 * 목록을 만든 뒤 (giftId, type) 으로 맞춰 붙인다.
 */
@Data
public class ReminderReadVO {

    private Long giftId;
    private ReminderType reminderType;
    private LocalDateTime readAt;
}
