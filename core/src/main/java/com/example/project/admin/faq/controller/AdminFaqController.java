package com.example.project.admin.faq.controller;

import com.example.project.admin.faq.service.AdminFaqService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@RestController
@Api(tags = "관리자 FAQ 관리 API")
@ApiLog
@RequestMapping("/api/admin/faq")
@RequiredArgsConstructor
public class AdminFaqController {

    private final AdminFaqService adminFaqService;

    @GetMapping
    public ApiResponse<AdminFaqService> getFaqPage(
            @RequestParam(defaultValue = "0")
            @Min(0)
            Integer page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String reportType,

            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    )
}
