package com.example.project.admin.lawtax.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class LawArticleRow {
    private Long lawId;
    private String lawCode;
    private String lawKey;
    private String lawName;
    private String lawType;
    private String ministry;
    private String promulgationNo;
    private LocalDate promulgationDate;
    private LocalDate effectiveDate;
    private String unitType;
    private String articleNo;
    private String articleKey;
    private String title;
    private LocalDate articleEffectiveDate;
    private String content;
    private String revisionHistory;
    private LocalDate latestRevisionDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
