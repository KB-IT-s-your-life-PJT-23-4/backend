package com.example.project.admin.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class AdminProductSummaryResponse {

    private final boolean available;
    private final LocalDate asOfDate;
    private final String version;
    private final long deposits;
    private final long savings;
    private final long etfs;

    public static AdminProductSummaryResponse unavailable() {
        return new AdminProductSummaryResponse(false, null, null, 0L, 0L, 0L);
    }
}
