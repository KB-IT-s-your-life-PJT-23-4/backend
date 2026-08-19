package com.example.project.reminder.mapper;

import com.example.project.reminder.domain.ProductReminderVO;
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

    /**
     * 만기 알림 대상. 증여로 등록된 시뮬레이션의 선택 예금·적금을 (증여, 상품) 쌍으로 편다.
     * 시뮬레이션 하나에 여러 행이 나오지만 giftId 와 maturityDate 는 같다.
     *
     * @param giftId null 이면 해당 유저 전체, 값이 있으면 그 증여가 속한 시뮬레이션만
     */
    List<ProductReminderVO> selectProductMaturities(@Param("userId") Long userId,
                                                    @Param("giftId") Long giftId);
}
