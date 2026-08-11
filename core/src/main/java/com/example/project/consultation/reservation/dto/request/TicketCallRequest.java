package com.example.project.consultation.reservation.dto.request;

import javax.validation.constraints.NotNull;

public record TicketCallRequest(
        @NotNull(message = "지점을 선택해주세요.")
        Long branchId
) {}