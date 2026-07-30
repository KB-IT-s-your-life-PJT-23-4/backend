package com.example.project.gift.domain;

import lombok.Data;

import java.time.LocalDate;

/** 수증자 1명의 10년 합산 공제 현황 집계. */
@Data
public class DeductionVO {

    private Long familyId;
    private String familyName;
    private String relation;
    private LocalDate birthDate;

    /** 조회 시점 기준 만 19세 미만. 한도 행을 고르는 데 쓴 값이라 응답에도 그대로 내보낸다. */
    private boolean minor;

    /** 관계·미성년 여부에 해당하는 한도 행이 없으면 null (예: 배우자). */
    private Long deductionLimit;

    /** 10년 창 안의 COMPLETED 증여 합계. */
    private Long usedAmount;

    /** 10년 창 안의 PLANNED 증여 합계. 한도 차감에는 넣지 않는다. */
    private Long plannedAmount;

    /** 합산에 들어간 COMPLETED 증여 건수. */
    private int aggregatedCount;

    /** 창 안에서 가장 오래된 COMPLETED 증여일. 이 날짜 + 10년이 한도 갱신일이다. 이력이 없으면 null. */
    private LocalDate oldestGiftDate;
}
