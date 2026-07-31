package com.example.project.gift.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilingInfoResponse {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private Long giftId;
    private Long familyId;
    private String familyName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate giftDate;
    private String status;
    // PLANNED 증여의 예상 세액이면 true
    private boolean estimated;
    private Long giftAmount;

    // 신고기한 = 증여일이 속한 달의 말일부터 3개월
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate filingDueDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate windowStartDate;

    // 합산 기준일. 공제 현황과 달리 오늘이 아니라 증여일이다.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate baseDate;

    // 10년간 확정된 증여액 총합
    private Long priorGiftAmount;
    // 공제 한도
    private Long deductionLimit;
    // 이번 증여에 공제된 액수
    private Long appliedDeduction;
    // 과세 표준
    private Long taxableBase;
    // 세율 구간
    private BigDecimal taxRate;
    // 산출 세액
    private Long calculatedTax;
    // 3% 할인액
    private Long filingCredit;
    // 총 납부해야 하는 세액
    private Long payableTax;
}
