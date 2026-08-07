package com.example.project.admin.dashboard.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DailySignupCount {

    private LocalDate date;
    private Long count;
}
