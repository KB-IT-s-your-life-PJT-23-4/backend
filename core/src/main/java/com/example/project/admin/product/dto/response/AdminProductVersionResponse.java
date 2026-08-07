package com.example.project.admin.product.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdminProductVersionResponse {
    private final Long productDataVersionId;
    private final String versionCode;
    private final LocalDate dataDate;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;
    private final Long depositCount;
    private final Long savingsCount;
    private final Long etfCount;
}