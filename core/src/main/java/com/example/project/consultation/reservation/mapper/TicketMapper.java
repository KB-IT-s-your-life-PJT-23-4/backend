package com.example.project.consultation.reservation.mapper;

import com.example.project.consultation.reservation.domain.BranchVO;
import com.example.project.consultation.reservation.domain.DeskTypeVO;
import com.example.project.consultation.reservation.domain.TicketVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TicketMapper {

    List<BranchVO> selectAllActiveBranches();

    BranchVO selectActiveBranch(@Param("branchId") Long branchId);

    Integer selectCounterForUpdate(
            @Param("branchId") Long branchId,
            @Param("deskTypeCode") String deskTypeCode,
            @Param("businessDate") LocalDate businessDate
    );

    int insertCounterIfAbsent(
            @Param("branchId") Long branchId,
            @Param("deskTypeCode") String deskTypeCode,
            @Param("businessDate") LocalDate businessDate
    );

    int incrementCounter(
            @Param("branchId") Long branchId,
            @Param("deskTypeCode") String deskTypeCode,
            @Param("businessDate") LocalDate businessDate,
            @Param("newNumber") int newNumber
    );

    DeskTypeVO selectDeskType(@Param("deskTypeCode") String deskTypeCode);

    void insertTicket(TicketVO ticket);

    TicketVO selectNextWaitingTicket(
            @Param("branchId") Long branchId,
            @Param("deskTypeCode") String deskTypeCode,
            @Param("businessDate") LocalDate businessDate
    );

    int updateTicketStatusToCalled(@Param("ticketId") Long ticketId);

    int countWaitingTickets(
            @Param("branchId") Long branchId,
            @Param("deskTypeCode") String deskTypeCode,
            @Param("businessDate") LocalDate businessDate
    );

    Integer selectLastCalledNumber(
            @Param("branchId") Long branchId,
            @Param("deskTypeCode") String deskTypeCode,
            @Param("businessDate") LocalDate businessDate
    );
}