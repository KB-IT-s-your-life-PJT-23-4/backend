package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.dto.response.FaqListResponse;
import com.example.project.consultation.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/faq")
    public ApiResponse<FaqListResponse> getFaqList(HttpServletRequest request) {
        FaqListResponse data = faqService.getFaqList();
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }
}