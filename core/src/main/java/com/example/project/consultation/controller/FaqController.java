package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.consultation.dto.response.FaqAnswerResponse;
import com.example.project.consultation.dto.response.FaqListResponse;
import com.example.project.consultation.service.FaqService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@ApiLog
@Api(tags = "FAQ API", description = "사용자에게 제공할 FAQ 목록과 답변을 조회합니다.")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/faq")
    @ApiOperation(value = "FAQ 목록 조회", notes = "카테고리와 질문으로 구성된 전체 FAQ 목록을 조회합니다.")
    public ApiResponse<FaqListResponse> getFaqList(HttpServletRequest request) {
        FaqListResponse data = faqService.getFaqList();
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping("/faq/{faqid}/answer")
    @ApiOperation(value = "FAQ 답변 조회", notes = "선택한 FAQ의 상세 답변을 조회합니다.")
    public ApiResponse<FaqAnswerResponse> getFaqAnswer(
            @ApiParam(value = "FAQ ID", required = true, example = "1") @PathVariable("faqid") Long faqId,
            HttpServletRequest request
    ) {
        FaqAnswerResponse data = faqService.getFaqAnswer(faqId);
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }
}
