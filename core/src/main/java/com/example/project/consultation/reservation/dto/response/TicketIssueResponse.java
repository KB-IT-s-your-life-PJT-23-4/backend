package com.example.project.consultation.dto.response;

import java.time.LocalDate;

public record TicketIssueResponse(
        Long ticketId,
        String ticketNumber,
        LocalDate businessDate
) {}