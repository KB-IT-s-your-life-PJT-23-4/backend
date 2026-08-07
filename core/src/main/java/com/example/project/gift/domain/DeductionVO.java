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

    /** 성년/미성년 구분 */
    private boolean minor;

    /** 관계·미성년 여부에 해당하는 한도 행이 없으면 null */
    private Long deductionLimit;

    /** 10년 윈도우 안의 COMPLETED 증여 합계. */
    private Long usedAmount;

    /** 10년 윈도우 안의 PLANNED 증여 합계. 한도 차감 x */
    private Long plannedAmount;

    /** 합산에 들어간 COMPLETED 증여 건수. */
    private int aggregatedCount;

    /**
     * 창 안에서 가장 오래된 COMPLETED 증여일, 이력이 없으면 null
     * 한도 갱신일은 여기서 바로 나오지 않는다. 한 건이 빠져도 여전히 한도 초과일 수 있어 서비스가 윈도우 안 증여를 각각 계산
     */
    private LocalDate oldestGiftDate;
}
