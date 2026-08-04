package com.example.project.simulation.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductDataVersionRecord {
    private Long productDataVersionId;
    private String versionCode;
    private LocalDate dataDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
