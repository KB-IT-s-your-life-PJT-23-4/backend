package com.example.project.consultation.reservation.dto.response;

public record TicketStatusResponse(
        Long branchId,
        int waitingCount,
        String currentCalledNumber
) {}