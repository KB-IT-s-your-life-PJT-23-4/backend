package com.example.project.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class AdminDashboardPeriodResponse {

    private final LocalDate from;
    private final LocalDate to;
}
