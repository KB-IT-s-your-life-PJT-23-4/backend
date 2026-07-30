package com.example.project.gift.dto.request;

import com.example.project.gift.domain.Status;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GiftRequest {

    private Long familyId;
    private Long amount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate giftDate;

    /** 미지정 시 PLANNED. */
    private Status status;

    private String memo;
}
