package com.example.project.reminder.dto.response;

import com.example.project.gift.domain.Status;
import com.example.project.reminder.domain.ReminderType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderResponse {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private ReminderType type;
    private Long giftId;
    private Long familyId;
    private String familyName;

    /**
     * 신고 대상 증여 금액, 갱신일이면 null
     *
     */
    private Long amount;

    /**
     * 대상 증여의 상태. 신고기한 알림에만 채워지고 갱신일 알림은 null.
     *
     * <p>예정일을 이체일로 보는 서비스라 PLANNED 여도 기한 값 자체는 확정분과 같다.
     * 다른 점은 필수 서류 확인이 안 끝났다는 것이라, 화면이 이 값으로 확정 유도 문구를 덧붙인다.
     */
    private Status status;

    /**
     * 신고기한, 공제 갱신일 다 포함한 개념
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate targetDate;

    /**
     * 알림함에 뜨기 시작하는 날. 리마인더를 저장하지 않아 "생성 시각"이 없으므로,프론트가 새 알림 여부를 판정하려면 이 날짜를 마지막 확인 시점과 비교
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate notifyFrom;

    /**
     * 남은 기간. 양수면 남았고 0 이면 당일, 음수면 기한이 지났다.
     */
    private long daysRemaining;

    /**
     * 알림함에서 확인한 시각. null 이면 안 읽음.
     * 신고기한이 지났는지, 안 읽었는지 같은 판단은 화면 표기라 프론트에 맡기고 값만 내려준다.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime readAt;

    public static ReminderResponse of(ReminderType type,
                                      Long giftId,
                                      Long familyId,
                                      String familyName,
                                      Long amount,
                                      Status status,
                                      LocalDate targetDate,
                                      LocalDate notifyFrom,
                                      LocalDate today,
                                      LocalDateTime readAt) {
        long daysRemaining = ChronoUnit.DAYS.between(today, targetDate);

        return new ReminderResponse(
                type,
                giftId,
                familyId,
                familyName,
                amount,
                status,
                targetDate,
                notifyFrom,
                daysRemaining,
                readAt
        );
    }
}
