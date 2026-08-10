package com.example.project.consultation.dto.request;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;

public record TicketIssueRequest(
        @NotNull(message = "지점을 선택해주세요.")
        Long branchId,

        @NotBlank(message = "신청 업무를 선택해주세요.")
        String serviceType
) {}