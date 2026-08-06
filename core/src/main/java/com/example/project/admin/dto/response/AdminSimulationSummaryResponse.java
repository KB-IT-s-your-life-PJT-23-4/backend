package com.example.project.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AdminSimulationSummaryResponse {

    private final long runs;
    private final long saves;
    private final BigDecimal saveRate;
}
