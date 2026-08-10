package com.example.project.consultation.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class BranchVO {
    private Long branchId;
    private String branchName;
    private BigDecimal latitude;
    private BigDecimal longitude;
}