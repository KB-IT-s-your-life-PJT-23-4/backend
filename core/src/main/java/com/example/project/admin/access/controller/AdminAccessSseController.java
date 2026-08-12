package com.example.project.admin.access.controller;

import com.example.project.admin.access.service.AdminAccessSseService;
import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.security.AdminAccessValidator;
import org.springframework.http.HttpHeaders;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Api(tags = "관리자 접근 로그 실시간 API")
@RestController
@RequestMapping("/api/admin/access-logs")
@RequiredArgsConstructor
public class AdminAccessSseController {

    private final AdminAccessSseService adminAccessSseService;
    private final AdminAccessValidator adminAccessValidator;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            Authentication authentication,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        AdminPrincipal admin = adminAccessValidator.requireRoot(authentication);

        SseEmitter emitter = adminAccessSseService.subscribe(
                admin.userId(),
                lastEventId
        );

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
