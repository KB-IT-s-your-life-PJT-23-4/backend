package com.example.project.consultation.reservation.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeskTypeVO {
    private String deskTypeCode;
    private String deskTypeName;
    private String prefix;
    private boolean operating;
}
