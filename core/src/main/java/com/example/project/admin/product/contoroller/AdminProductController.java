package com.example.project.admin.product.contoroller;

import com.example.project.admin.product.dto.request.AdminProductDataVersionCreateRequest;
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
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<AdminProductVersionResponse> data = adminProductService.getProductDataVersions();
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @PostMapping("/versions")
    public ApiResponse<AdminProductVersionResponse> createProductDataVersion(
            @Valid @RequestBody AdminProductDataVersionCreateRequest createRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        AdminProductVersionResponse data =
                adminProductService.createProductDataVersion(createRequest);
        return ApiResponse.success(ResponseCode.CREATED, request.getRequestURI(), data);
    }

    @GetMapping("/versions/{productDataVersionId}/products")
    public ApiResponse<List<AdminProductResponse>> getProducts(
            @PathVariable Long productDataVersionId,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<AdminProductResponse> data =
                adminProductService.getProducts(productDataVersionId, type);
        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @PatchMapping("/versions/{productDataVersionId}/products/{productVersionId}")
    public ApiResponse<AdminProductResponse> updateProduct(
            @PathVariable Long productDataVersionId,
            @PathVariable Long productVersionId,
            @Valid @RequestBody AdminProductUpdateRequest updateRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        AdminProductResponse data = adminProductService.updateProduct(
                productDataVersionId, productVersionId, updateRequest
        );
        return ApiResponse.success(ResponseCode.UPDATED, request.getRequestURI(), data);
    }
}