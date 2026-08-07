package com.example.project.admin.product.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminProductVersionRow {
    private Long productDataVersionId;
    private String versionCode;
    private LocalDate dataDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Long depositCount;
    private Long savingsCount;
    private Long etfCount;
}