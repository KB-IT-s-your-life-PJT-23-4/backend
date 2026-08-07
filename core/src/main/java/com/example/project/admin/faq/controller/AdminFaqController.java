package com.example.project.admin.faq.controller;

import com.example.project.admin.faq.dto.request.AdminCategoryRequest;
import com.example.project.admin.faq.dto.response.AdminCategoryResponse;
import com.example.project.admin.faq.dto.response.AdminFaqCategoriesDto;
import com.example.project.admin.faq.dto.response.AdminFaqPageResponse;
import com.example.project.admin.faq.service.AdminFaqService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@RestController
@Api(tags = "관리자 FAQ 관리 API")
@ApiLog
@Validated
@RequestMapping("/api/admin/faq")
@RequiredArgsConstructor
public class AdminFaqController {

    private final AdminFaqService adminFaqService;

    // 초기 화면 진입 시 조회 매서드
    @GetMapping
    public ApiResponse<AdminFaqPageResponse> getFaqPage(
            @RequestParam(defaultValue = "0")
            @Min(0)
            Integer page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(Pagination.MAX_PAGE_SIZE)
            int size,

            @RequestParam(required = false)
            Long categoryId,

            @RequestParam(required = false)
            String keyword,

            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ){

        AdminFaqPageResponse response = adminFaqService.getFaqPage(
                page, size, categoryId, keyword
        );

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @GetMapping("/category")
    public ApiResponse<AdminFaqCategoriesDto> getFaqCategories(
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ){

        AdminFaqCategoriesDto response = adminFaqService.getCategories();

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }
    //------------------------------------------------------------------------------


    @PostMapping("/category")
    public ApiResponse<AdminCategoryResponse> createCategory(
            @Valid @RequestBody AdminCategoryRequest request,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ){

        AdminCategoryResponse response = adminFaqService.createCategory(request);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @PatchMapping("/category")
    public ApiResponse<AdminCategoryResponse> updateCategory(
            @Valid @RequestBody AdminCategoryRequest request,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ){
        AdminCategoryResponse response = adminFaqService.updateCategory(request);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), response);
    }

    @DeleteMapping("/category/{categoryId}")
    public ApiResponse<Void> deleteCategory(
            @Valid @PathVariable long categoryId,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ){
        adminFaqService.deleteCategory(categoryId);

        return ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), null);
    }

    @PostMapping()


    @PatchMapping("/{faqId}")


    @DeleteMapping("/{faqId}")

}
