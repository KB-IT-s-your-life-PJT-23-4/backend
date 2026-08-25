package com.example.project.consultation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiSafetyReportVO {

    //AI 안전 신고 ID
    private Long aiSafetyReportId;

    //중복 신고 방지 키
    private String reportKey;

    //관리자 처리 상태
    private String status;

    //신고 유형
    private String reportType;

    //신고 대상 사용자 ID
    private Long userId;

    //신고를 발생시킨 AI 상담 이벤트 ID
    private Long triggerEventId;

    //개인정보를 제거한 신고 범위의 질문 목록
    @Builder.Default
    private List<String> questionExcerpts = List.of();

    //신고 시점 발생 횟수
    private Integer occurrenceCount;

    //other 발생 횟수 집계 시작 시각
    private LocalDateTime countWindowStartedAt;

    //other 발생 횟수 집계 종료 시각
    private LocalDateTime countWindowEndedAt;

    //담장 관리자 ID
    private Long assignedAdminId;

    //관리자 처리 내용
    private String resolutionNote;

    //관리자 검토 완료 시각
    private LocalDateTime reviewedAt;

    //신고 생성 시각
    private LocalDateTime createdAt;

    //신고 수정 시각
    private LocalDateTime updatedAt;

}
