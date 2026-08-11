package com.example.project.consultation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreparedConversation {

    private Long aiConversationId;
    private Long userId;
    private String conversationId;
    private String requestId;
    private Integer turnNo;
    private String questionExcerpt;
}
