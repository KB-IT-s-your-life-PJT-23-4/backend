package com.example.project.admin.dashboard.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminDashboardErrorCount {

    private Long http422;
    private Long http500;
    private Long timeout;
}
