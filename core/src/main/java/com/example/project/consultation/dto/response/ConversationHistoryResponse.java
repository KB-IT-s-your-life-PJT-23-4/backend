package com.example.project.consultation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistoryResponse {

    private String conversationId;
    private String status;
    private List<ConversationTurnResponse> turns;
}
