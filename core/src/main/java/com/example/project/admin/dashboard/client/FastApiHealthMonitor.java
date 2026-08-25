package com.example.project.admin.dashboard.client;

import com.example.project.admin.dashboard.dto.response.AdminDashboardResponse;

@FunctionalInterface
public interface FastApiHealthMonitor {

    AdminDashboardResponse.FastApiMetrics check();
}
