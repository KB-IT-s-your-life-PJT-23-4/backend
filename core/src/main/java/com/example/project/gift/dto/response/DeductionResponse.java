package com.example.project.gift.dto.response;

import com.example.project.gift.domain.DeductionVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeductionResponse {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private Long familyId;
    private String familyName;
    private String relation;
    private boolean minor;

    /** 합산 대상 구간. 프론트가 "언제부터 언제까지 합산한 값"인지 그대로 보여줄 수 있게 내려준다. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate windowStartDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate baseDate;

    /** 배우자처럼 한도 행이 없는 관계는 null. 이때 remainingAmount 도 null. */
    private Long deductionLimit;

    private Long usedAmount;

    /** 계획(PLANNED) 증여 합계. usedAmount 에는 포함되지 않는다. */
    private Long plannedAmount;

    /** 한도 - 사용액. 이미 초과했으면 0. */
    private Long remainingAmount;

    /** 계획까지 모두 실행됐다고 가정한 잔여 공제. 초과 시 0. */
    private Long remainingAmountIfPlanned;

    /** 합산에 들어간 확정 증여 건수. */
    private int aggregatedCount;

    /**
     * 가장 오래된 증여가 10년 창을 벗어나 한도가 되살아나는 날. 확정 증여가 없으면 null.
     * 라벨("7년 6개월")은 화면 표기라 프론트에 맡기고 날짜만 내려준다.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate nextRenewalDate;

    /** nextRenewalDate 는 10년 창 규칙을 쥔 서비스가 계산해 넘긴다. 확정 증여가 없으면 null. */
    public static DeductionResponse from(DeductionVO deduction,
                                         LocalDate windowStartDate,
                                         LocalDate baseDate,
                                         LocalDate nextRenewalDate) {
        Long limit = deduction.getDeductionLimit();
        long used = deduction.getUsedAmount() == null ? 0L : deduction.getUsedAmount();
        long planned = deduction.getPlannedAmount() == null ? 0L : deduction.getPlannedAmount();

        return new DeductionResponse(
                deduction.getFamilyId(),
                deduction.getFamilyName(),
                deduction.getRelation(),
                deduction.isMinor(),
                windowStartDate,
                baseDate,
                limit,
                used,
                planned,
                remaining(limit, used),
                remaining(limit, used + planned),
                deduction.getAggregatedCount(),
                nextRenewalDate
        );
    }

    private static Long remaining(Long limit, long consumed) {
        return limit == null ? null : Math.max(0L, limit - consumed);
    }
}
