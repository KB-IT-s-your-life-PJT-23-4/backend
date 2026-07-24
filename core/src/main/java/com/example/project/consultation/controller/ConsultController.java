package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.dto.request.ConsultRequest;
import com.example.project.consultation.service.ConsultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ConsultController {

    private final ConsultService consultService;

    @PostMapping("/consult")
    public ApiResponse<Void> consult(
            @RequestBody ConsultRequest request,
            HttpServletRequest httpRequest
            // TODO: 인증 사용자 정보 주입 방식 확정 후 파라미터 추가
    ) {
        consultService.validateQuestion(request.question());

        // TODO: 이후 FastAPI 호출, DB 저장, 응답 조립

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), null);
    }
}