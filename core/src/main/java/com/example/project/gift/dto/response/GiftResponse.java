package com.example.project.gift.dto;

import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GiftResponse {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private Long giftId;
    private Long familyId;
    private Long amount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate giftDate;

    private Status status;
    private String memo;

    public static GiftResponse from(GiftVO gift) {
        return new GiftResponse(
                gift.getGiftId(),
                gift.getFamilyId(),
                gift.getAmount(),
                gift.getGiftDate(),
                gift.getStatus(),
                gift.getMemo()
        );
    }
}
