package com.example.project.admin.product.controller;

import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.product.dto.request.AdminProductCreateRequest;
import com.example.project.admin.product.dto.request.AdminProductUpdateRequest;
import com.example.project.admin.product.dto.response.AdminProductResponse;
import com.example.project.admin.product.dto.response.AdminProductVersionResponse;
import com.example.project.admin.product.service.AdminProductService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@ApiLog
@Api(tags = "관리자 상품 관리 API")
@RestController
@RequestMapping("/api/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    @GetMapping("/versions")
    public ApiResponse<List<AdminProductVersionResponse>> getProductDataVersions(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        List<AdminProductVersionResponse> data = adminProductService.getProductDataVersions();
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @PostMapping("/versions")
    public ApiResponse<AdminProductVersionResponse> createDraftVersion(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        AdminProductVersionResponse data = adminProductService.createDraftVersionFromLatest(principal);
        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @PatchMapping("/versions/{productDataVersionId}/complete")
    public ApiResponse<AdminProductVersionResponse> completeProductDataVersion(
            @PathVariable Long productDataVersionId,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        AdminProductVersionResponse data =
                adminProductService.completeProductDataVersion(productDataVersionId, principal);
        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @GetMapping("/versions/{productDataVersionId}/products")
    public ApiResponse<List<AdminProductResponse>> getProducts(
            @PathVariable Long productDataVersionId,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        List<AdminProductResponse> data =
                adminProductService.getProducts(productDataVersionId, type);
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @PostMapping("/versions/{productDataVersionId}/products")
    public ApiResponse<AdminProductResponse> createProduct(
            @PathVariable Long productDataVersionId,
            @Valid @RequestBody AdminProductCreateRequest createRequest,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        AdminProductResponse data =
                adminProductService.createProduct(productDataVersionId, createRequest, principal);
        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @PatchMapping("/versions/{productDataVersionId}/products/{productVersionId}")
    public ApiResponse<AdminProductResponse> updateProduct(
            @PathVariable Long productDataVersionId,
            @PathVariable Long productVersionId,
            @Valid @RequestBody AdminProductUpdateRequest updateRequest,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        AdminProductResponse data = adminProductService.updateProduct(
                productDataVersionId, productVersionId, updateRequest, principal
        );
        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }

    @DeleteMapping("/versions/{id}")
    public ApiResponse<Void> deleteVersion(
            @PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal
    ) {
        adminProductService.deleteProductDataVersion(id, principal);
        return ApiResponse.success(ResponseCode.DELETED, ResponseCode.DELETED.getMessage(), null);
    }
}
