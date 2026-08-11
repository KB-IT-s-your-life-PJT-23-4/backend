package com.example.project.consultation.domain;

import com.example.project.consultation.dto.fastapi.ConversationContextMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContextSnapshot {

    private List<ConversationContextMessage> messages;

    private Map<String, Object> facts;
}
