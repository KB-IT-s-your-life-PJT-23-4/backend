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
import java.util.List;

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

    /**
     * 만기가 도래하는 예금·적금 상품명. 만기 알림에만 채워지고 나머지 유형은 null.
     *
     * <p>만기일은 시뮬레이션이 갖고 상품들이 공유하므로 알림은 한 건이고 상품은 여러 개다.
     * 어느 상품이 만기인지는 화면 문구에 필요해 목록으로 내린다.
     */
    private List<String> productNames;

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
                readAt,
                null
        );
    }

    /**
     * 만기 알림. 금액·상태 자리는 비고 상품명 목록이 대신 들어간다.
     *
     * <p>만기에는 신고기한처럼 걸린 금액이 없고(원금은 증여액이 아니라 투자 원금이다)
     * 확정 여부도 무관해 두 필드를 null 로 둔다.
     */
    public static ReminderResponse ofMaturity(Long giftId,
                                              Long familyId,
                                              String familyName,
                                              LocalDate targetDate,
                                              LocalDate notifyFrom,
                                              LocalDate today,
                                              LocalDateTime readAt,
                                              List<String> productNames) {
        ReminderResponse response = of(
                ReminderType.PRODUCT_MATURITY,
                giftId,
                familyId,
                familyName,
                null,
                null,
                targetDate,
                notifyFrom,
                today,
                readAt
        );

        response.setProductNames(productNames);

        return response;
    }
}
