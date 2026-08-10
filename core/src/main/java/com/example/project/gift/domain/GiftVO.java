package com.example.project.gift.domain;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GiftVO {
    private Long giftId;
    private Long familyId;
    private Long simulResultId;
    private Integer sequenceNo;
    private Long amount;
    private LocalDate giftDate;
    private Status status;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
