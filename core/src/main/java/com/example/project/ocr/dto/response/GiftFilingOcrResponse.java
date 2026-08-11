package com.example.project.ocr.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class GiftFilingOcrResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate giftDate;

    private Long amount;

    private String giftType;

    private String recipientName;

    private boolean amountVerified;

    private List<String> warnings = new ArrayList<>();
}
