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
     * 공제 여력이 실제로 생기는 첫 날. 합산액이 한도를 크게 넘긴 상태면 가장 오래된 증여가
     * 창을 벗어나도 여전히 초과라 여력이 0 그대로일 수 있어, 남는 합이 한도 밑으로 내려가는
     * 시점을 잡는다. 확정 증여가 없거나 한도 행이 없는 관계면 null.
     * 라벨("7년 6개월")은 화면 표기라 프론트에 맡기고 날짜만 내려준다.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate nextRenewalDate;

    /**
     * nextRenewalDate 를 끌어낸 증여의 id. 창에서 이 증여가 빠지는 날이 곧 갱신일이다.
     * 갱신일 리마인더를 "(gift_id, type)" 한 쌍으로 식별하기 위해 함께 내려준다.
     * nextRenewalDate 가 null 이면 이것도 null.
     */
    private Long renewalGiftId;

    /**
     * 갱신일에 늘어나는 공제 여력. "그날 얼마가 다시 생기나"를 그대로 보여주려고 서버가 계산한다.
     *
     * <p>창에서 빠지는 증여액과 같지 않다. 이미 한도를 넘긴 상태면 빠지는 금액 중 일부는
     * 초과분을 메우는 데 쓰이고 나머지만 여력이 된다. 그래서 갱신 후 잔여에서 지금 잔여를 뺀 값이다.
     * nextRenewalDate 가 null 이면 이것도 null.
     */
    private Long renewalAmount;

    /** nextRenewalDate 는 10년 창 규칙을 쥔 서비스가 계산해 넘긴다. 확정 증여가 없으면 null. */
    public static DeductionResponse from(DeductionVO deduction,
                                         LocalDate windowStartDate,
                                         LocalDate baseDate,
                                         LocalDate nextRenewalDate,
                                         Long renewalGiftId,
                                         Long renewalAmount) {
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
                nextRenewalDate,
                renewalGiftId,
                renewalAmount
        );
    }

    private static Long remaining(Long limit, long consumed) {
        return limit == null ? null : Math.max(0L, limit - consumed);
    }
}
