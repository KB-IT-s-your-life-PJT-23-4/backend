package com.example.project.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class AdminSignupSummaryResponse {

    private final long today;
    private final long last7Days;
    private final List<DailySignupResponse> trend;

    @Getter
    @AllArgsConstructor
    public static class DailySignupResponse {

        private final LocalDate date;
        private final long count;
    }
}
