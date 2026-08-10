package com.example.project.consultation.reservation.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class TicketVO {
    private Long ticketId;
    private Long branchId;
    private String deskTypeCode;
    private String serviceType;
    private int ticketNumber;
    private Long userId;
    private String status;
    private LocalDate businessDate;

    @Builder
    public TicketVO(Long branchId, String deskTypeCode, String serviceType,
                    int ticketNumber, Long userId, String status, LocalDate businessDate) {
        this.branchId = branchId;
        this.deskTypeCode = deskTypeCode;
        this.serviceType = serviceType;
        this.ticketNumber = ticketNumber;
        this.userId = userId;
        this.status = status;
        this.businessDate = businessDate;
    }
}