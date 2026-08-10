package com.example.project.consultation.reservation.dto.response;

import java.time.LocalDate;

public record TicketIssueResponse(
        Long ticketId,
        String ticketNumber,
        int waitingCount,
        LocalDate businessDate
) {}