package com.example.project.admin.dashboard.service;

import com.example.project.admin.dashboard.mapper.AdminDashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Log4j2
public class AdminDashboardErrorRecorder {

    public static final String RESULT_HTTP_ERROR = "HTTP_ERROR";
    public static final String RESULT_TIMEOUT = "TIMEOUT";

    private final AdminDashboardMapper adminDashboardMapper;
    private final Clock clock;

    public void record(
            String httpMethod,
            String requestUri,
            int responseStatus,
            boolean timeout,
            long elapsedMs
    ) {
        try {
            adminDashboardMapper.insertApiErrorLog(
                    httpMethod,
                    requestUri,
                    responseStatus,
                    timeout ? RESULT_TIMEOUT : RESULT_HTTP_ERROR,
                    Math.max(0L, elapsedMs),
                    LocalDateTime.now(clock)
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to persist dashboard error metric. method={}, uri={}, status={}, timeout={}",
                    httpMethod,
                    requestUri,
                    responseStatus,
                    timeout,
                    exception
            );
        }
    }
}
