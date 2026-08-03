package com.example.project.consultation.domain;

import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FamilyPreviousGiftVO {

//    사용자 식별자
    private Long userId;
//    가족 식별자
    private Long familyId;
//    가족 이름
    private String name;
//    조회일 기준 가족 나이
    private Integer recipientAge;
//    최근 10년 이내 증여 내역 존재 여부
    private boolean hasPreviousGifts;
//    최근 10년 이내 증여 금액 합계
//    증여 이력이 없으면 null
    private Long previousGiftAmount;
//    최근 10년 이내 가장 최근 증여일
    private LocalDate previousGiftDate;
//    최근 10년 이내 가장 오래된 증여일을 기준으로 계산한 다음 공제 갱신 예정일
    private LocalDate deductionRenewalDate;
}
