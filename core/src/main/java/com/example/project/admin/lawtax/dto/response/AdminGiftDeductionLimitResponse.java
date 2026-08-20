package com.example.project.admin.lawtax.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminGiftDeductionLimitResponse {
    private Long deductionLimitId;
    private String relation;
    private boolean minor;
    private Long deductionLimit;
}
