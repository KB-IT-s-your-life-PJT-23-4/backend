package com.example.project.gift.domain;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 증여세 신고기한 규칙(상증법 제68조).
 *
 * <p>DB 도 상태도 타지 않는 순수 날짜 계산이라 서비스가 아니라 도메인에 둔다.
 * 신고 안내와 리마인더가 같은 기한을 봐야 해서 규칙을 한 곳에 모아 둔 것이기도 하다.
 */
public final class FilingDeadline {

    private static final int DUE_MONTH = 3;

    private FilingDeadline() {
    }

    /**
     * 신고기한 = 증여일이 속하는 달의 말일부터 3개월.
     * 초일불산입이라 "말일 + 3개월"이 아니라 3개월 뒤 달의 말일이 된다.
     * 예) 4/10 증여 -> 7/31 (4/30 에 3개월을 더한 7/30 이 아니다)
     */
    public static LocalDate of(LocalDate giftDate) {
        return giftDate.plusMonths(DUE_MONTH).with(TemporalAdjusters.lastDayOfMonth());
    }
}
