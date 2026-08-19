package com.example.project.simulation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CustomPortfolioResponse {

    private final SimulationResponse simulation;
    private final Long savingsMaximumAmount;
    private final BigDecimal savingsMaximumRatio;
}
