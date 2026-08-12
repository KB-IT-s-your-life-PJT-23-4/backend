package com.example.project.admin.access.domain;

import io.swagger.annotations.Api;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccessEvent {

    private String eventId;

    private Long userId;
    private String role;

    private String httpMethod;
    private String requestUri;

    private String controllerName;
    private String controllerMethod;

    private String result;
    private String exceptionName;

    private long elapsedMs;
    private LocalDateTime occurredAt;
}
