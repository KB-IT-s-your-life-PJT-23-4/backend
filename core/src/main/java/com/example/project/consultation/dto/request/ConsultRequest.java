package com.example.project.consultation.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;


public record ConsultRequest(
        String question
) {}
