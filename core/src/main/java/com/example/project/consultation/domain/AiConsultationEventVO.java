package com.example.project.consultation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConsultationEventVO {

    //AI 상담 분류 이밴트 ID
    private Long aiConsultationEventId;
    //AI 상담 ID
    private Long aiConversationId;
    //FastAPI에서 생성한 상담 ID
    private String conversationId;
    //질문 요청 ID
    private String requestId;
    //질문 순서 번호
    private Integer turnNo;
    //상담 요청 유저 ID
    private Long userId;
    //Fast API가 분류한 질문 유형
    private String intent;
    //응답 상태
    private String responseStatus;
    //개인 정보를 제거한 질문 일부
    private String questionExcerpt;
    //AI 분류가 발생한 시각
    private LocalDateTime occurredAt;
    //DB 생성 시각
    private LocalDateTime createdAt;
}
