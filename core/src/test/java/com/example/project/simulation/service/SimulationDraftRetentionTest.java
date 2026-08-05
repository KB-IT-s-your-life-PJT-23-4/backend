package com.example.project.simulation.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationDraftRetentionTest {

    @Test
    void keepsDraftForOneCalendarMonth() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 31, 10, 30);

        assertEquals(
                LocalDateTime.of(2026, 2, 28, 10, 30),
                createdAt.plus(SimulationService.DRAFT_RETENTION)
        );
    }
}
