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
public class AiConversationVO {
    private Long aiConversationId;
    private String conversationId;
    private Long userId;
    private String status;
    private String processingStatus;
    private LocalDateTime processingStartedAt;
    /*
     * MyBatis에서는 JSON 컬럼을 우선 String으로 받습니다.
     * Jackson을 통해 읽고 수정한 뒤 다시 String으로 저장합니다.
     */
    private String transcriptJson;
    private Integer turnCount;
    private Long version;
    private LocalDateTime lastMessageAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
