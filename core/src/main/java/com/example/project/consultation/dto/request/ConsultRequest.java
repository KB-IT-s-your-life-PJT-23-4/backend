package com.example.project.consultation.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public final class ConsultRequest {

    private final String question;

    public String question() {
        return question;
    }
}
