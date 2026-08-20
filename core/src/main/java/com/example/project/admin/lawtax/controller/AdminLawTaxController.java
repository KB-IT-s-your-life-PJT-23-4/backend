package com.example.project.admin.lawtax.controller;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.lawtax.dto.request.AdminGiftTaxVersionRequest;
import com.example.project.admin.lawtax.dto.response.AdminGiftTaxVersionResponse;
import com.example.project.admin.lawtax.dto.response.AdminLawArticlePageResponse;
import com.example.project.admin.lawtax.dto.response.AdminLawArticleResponse;
import com.example.project.admin.lawtax.dto.response.AdminLawSummaryResponse;
import com.example.project.admin.lawtax.service.AdminLawTaxService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;

@ApiLog
@Api(tags = "관리자 세법 관리 API")
@RestController
@Validated
@RequestMapping("/api/admin/lawtax")
@RequiredArgsConstructor
public class AdminLawTaxController {

    private final AdminLawTaxService adminLawTaxService;

    @GetMapping("/versions")
    public ApiResponse<List<AdminGiftTaxVersionResponse>> getVersions(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), adminLawTaxService.getVersions());
    }

    @PostMapping("/versions")
    public ApiResponse<AdminGiftTaxVersionResponse> createVersion(
            @Valid @RequestBody AdminGiftTaxVersionRequest createRequest,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        AdminGiftTaxVersionResponse data = adminLawTaxService.createVersion(createRequest, principal);
        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @PutMapping("/versions/{effectiveFrom}")
    public ApiResponse<AdminGiftTaxVersionResponse> updateVersion(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @Valid @RequestBody AdminGiftTaxVersionRequest updateRequest,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        AdminGiftTaxVersionResponse data =
                adminLawTaxService.updateVersion(effectiveFrom, updateRequest, principal);
        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @DeleteMapping("/versions/{effectiveFrom}")
    public ApiResponse<Void> deleteVersion(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @AuthenticationPrincipal AdminPrincipal principal
    ) {
        adminLawTaxService.deleteVersion(effectiveFrom, principal);
        return ApiResponse.success(ResponseCode.DELETED, ResponseCode.DELETED.getMessage(), null);
    }

    @GetMapping("/articles")
    public ApiResponse<AdminLawArticlePageResponse> getArticlePage(
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Pagination.MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) String lawCode,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        AdminLawArticlePageResponse response =
                adminLawTaxService.getLawArticlePage(page, size, lawCode, keyword);
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), response);
    }

    @GetMapping("/articles/laws")
    public ApiResponse<List<AdminLawSummaryResponse>> getLawSummaries(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), adminLawTaxService.getLawSummaries());
    }

    @GetMapping("/articles/{lawId}")
    public ApiResponse<AdminLawArticleResponse> getArticle(
            @PathVariable Long lawId,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), adminLawTaxService.getLawArticle(lawId));
    }
}
