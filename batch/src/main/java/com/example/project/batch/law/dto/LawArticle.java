package com.example.project.batch.law.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LawArticle {

    private String lawName;
    private String articleNo;
    private String title;
    private String content;
    private LocalDate effectiveDate;

    public LawArticle() {
    }
}
