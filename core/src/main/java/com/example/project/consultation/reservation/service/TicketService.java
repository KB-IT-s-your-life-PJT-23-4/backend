package com.example.project.consultation.reservation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.reservation.domain.BranchVO;
import com.example.project.consultation.reservation.domain.DeskTypeVO;
import com.example.project.consultation.reservation.domain.TicketVO;
import com.example.project.consultation.reservation.dto.response.TicketCallResponse;
import com.example.project.consultation.reservation.dto.response.TicketIssueResponse;
import com.example.project.consultation.reservation.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final String PERSONAL_DESK_TYPE = "PERSONAL";
    private static final String WAITING = "WAITING";
    private static final Set<String> ALLOWED_SERVICE_TYPES = Set.of(
            "DEPOSIT_SAVINGS_FUND_TRUST", "PERSONAL_LOAN"
    );

    private final TicketMapper ticketMapper;

    @Transactional
    public TicketIssueResponse issueTicket(Long branchId, String serviceType, Long userId) {
        if (!ALLOWED_SERVICE_TYPES.contains(serviceType)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        BranchVO branch = ticketMapper.selectActiveBranch(branchId);
        if (branch == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }

        LocalDate businessDate = LocalDate.now();

        ticketMapper.insertCounterIfAbsent(branchId, PERSONAL_DESK_TYPE, businessDate);

        Integer currentNumber = ticketMapper.selectCounterForUpdate(branchId, PERSONAL_DESK_TYPE, businessDate);
        if (currentNumber == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        int newNumber = currentNumber + 1;
        ticketMapper.incrementCounter(branchId, PERSONAL_DESK_TYPE, businessDate, newNumber);

        DeskTypeVO deskType = ticketMapper.selectDeskType(PERSONAL_DESK_TYPE);
        if (deskType == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        TicketVO ticket = TicketVO.builder()
                .branchId(branchId)
                .deskTypeCode(PERSONAL_DESK_TYPE)
                .serviceType(serviceType)
                .ticketNumber(newNumber)
                .userId(userId)
                .status(WAITING)
                .businessDate(businessDate)
                .build();

        ticketMapper.insertTicket(ticket);

        String displayNumber = deskType.getPrefix() + "-" + newNumber;

        return new TicketIssueResponse(ticket.getTicketId(), displayNumber, businessDate);
    }

    @Transactional
    public TicketCallResponse callNextTicket(Long branchId) {
        LocalDate businessDate = LocalDate.now();

        TicketVO nextTicket = ticketMapper.selectNextWaitingTicket(branchId, PERSONAL_DESK_TYPE, businessDate);
        if (nextTicket == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND); // 대기 중인 손님 없음
        }

        int updated = ticketMapper.updateTicketStatusToCalled(nextTicket.getTicketId());
        if (updated == 0) {
            throw new ServiceException(ResponseCode.CONFLICT); // 그 사이 다른 요청이 먼저 처리함
        }

        DeskTypeVO deskType = ticketMapper.selectDeskType(PERSONAL_DESK_TYPE);
        String displayNumber = deskType.getPrefix() + "-" + nextTicket.getTicketNumber();

        return new TicketCallResponse(nextTicket.getTicketId(), displayNumber);
    }
}