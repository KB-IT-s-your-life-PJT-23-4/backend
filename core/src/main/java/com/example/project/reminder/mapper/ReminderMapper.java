package com.example.project.reminder.mapper;

import com.example.project.reminder.domain.ReminderReadVO;
import com.example.project.reminder.domain.ReminderType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReminderMapper {

    /**
     * 읽음 기록. 리마인더 행 자체가 없으면 만들고, 있으면 read_at 만 갱신한다.
     * uk_reminder_gift_type 이 있어 같은 알림을 여러 번 읽어도 행은 하나다.
     *
     * <p>소유권 검증은 여기서 하지 않는다. 서비스가 증여 소유자를 먼저 확인한다.
     */
    int upsertRead(@Param("giftId") Long giftId,
                   @Param("reminderType") ReminderType reminderType,
                   @Param("targetDate") LocalDate targetDate,
                   @Param("readAt") LocalDateTime readAt);

    /** 해당 유저의 읽음 기록 전부. gift → family 조인으로 남의 기록은 걸러진다. */
    List<ReminderReadVO> selectReads(@Param("userId") Long userId);
}
