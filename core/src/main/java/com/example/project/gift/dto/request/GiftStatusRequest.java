package com.example.project.gift.dto.request;

import com.example.project.gift.domain.Status;
import lombok.Data;

@Data
public class GiftStatusRequest {

    private Status status;
}
