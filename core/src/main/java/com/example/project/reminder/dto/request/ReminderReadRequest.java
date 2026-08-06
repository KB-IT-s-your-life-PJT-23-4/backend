package com.example.project.reminder.dto.request;

import com.example.project.reminder.domain.ReminderType;
import lombok.Data;

/**
 * 읽음 처리 대상. 리마인더는 저장하지 않아 식별자가 따로 없으므로
 * 목록이 내려준 (giftId, type) 한 쌍을 그대로 돌려받는다.
 */
@Data
public class ReminderReadRequest {

    private Long giftId;
    private ReminderType type;
}
