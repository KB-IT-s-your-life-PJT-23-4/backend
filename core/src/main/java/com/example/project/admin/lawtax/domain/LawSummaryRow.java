package com.example.project.admin.lawtax.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LawSummaryRow {
    private String lawCode;
    private String lawName;
    private String lawType;
}
