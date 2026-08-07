package com.example.project.admin.dashboard.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class LatestProductDataVersion {

    private Long productDataVersionId;
    private String versionCode;
    private LocalDate dataDate;
}
