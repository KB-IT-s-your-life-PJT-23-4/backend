package com.example.project.admin.dashboard.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsultationDashboardCount {

    private Long requests;
    private Long successes;
    private Long failures;
}
