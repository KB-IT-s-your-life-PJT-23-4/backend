package com.example.project.gift.dto.request;

import com.example.project.gift.domain.Status;
import lombok.Data;

/** 상태 변경용 Req */
@Data
public class GiftStatusRequest {

    private Status status;
}
