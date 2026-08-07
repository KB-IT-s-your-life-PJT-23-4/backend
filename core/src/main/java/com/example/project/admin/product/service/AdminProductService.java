package com.example.project.admin.product.service;

import com.example.project.admin.product.domain.AdminProductRow;
import com.example.project.admin.product.domain.AdminProductVersionRow;
import com.example.project.admin.product.dto.request.AdminProductDataVersionCreateRequest;
import com.example.project.admin.product.dto.request.AdminProductUpdateRequest;
import com.example.project.admin.product.dto.response.AdminProductResponse;
import com.example.project.admin.product.dto.response.AdminProductVersionResponse;
import com.example.project.admin.product.mapper.AdminProductMapper;
import com.example.project.common.api.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.project.common.exception.ServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private static final Set<String> PRODUCT_TYPES = Set.of("DEPOSIT", "SAVINGS", "ETF");

    private final AdminProductMapper adminProductMapper;

    @Transactional(readOnly = true)
    public List<AdminProductVersionResponse> getProductDataVersions() {
        return adminProductMapper.selectProductDataVersions().stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> getProducts(Long productDataVersionId, String typeValue) {
        requireDataVersion(productDataVersionId);
        String type = normalizeType(typeValue);

        List<AdminProductRow> rows = new ArrayList<>();
        if (type == null || type.equals("DEPOSIT")) {
            rows.addAll(adminProductMapper.selectDepositProducts(productDataVersionId, null));
        }
        if (type == null || type.equals("SAVINGS")) {
            rows.addAll(adminProductMapper.selectSavingsProducts(productDataVersionId, null));
        }
        if (type == null || type.equals("ETF")) {
            rows.addAll(adminProductMapper.selectEtfProducts(productDataVersionId, null));
        }

        return rows.stream().map(this::toProductResponse).toList();
    }

    @Transactional
    public AdminProductResponse updateProduct(
            Long productDataVersionId,
            Long productVersionId,
            AdminProductUpdateRequest request
    ) {
        requireDataVersion(productDataVersionId);

        String productType = adminProductMapper.selectProductType(productVersionId);
        if (productType == null) {
            throw new ServiceException(ResponseCode.PRODUCT_NOT_FOUND);
        }

        AdminProductRow existing = fetchSingle(productType, productVersionId);
        if (existing == null || !productDataVersionId.equals(existing.getProductDataVersionId())) {
            throw new ServiceException(ResponseCode.PRODUCT_NOT_FOUND);
        }

        int updated = adminProductMapper.updateProductVersion(
                productVersionId,
                request.getProductName(),
                request.getDescription(),
                request.getProductUrl(),
                request.getSalesStatus()
        );
        if (updated == 0) {
            throw new ServiceException(ResponseCode.PRODUCT_NOT_FOUND);
        }

        return toProductResponse(fetchSingle(productType, productVersionId));
    }

    @Transactional
    public AdminProductVersionResponse createProductDataVersion(
            AdminProductDataVersionCreateRequest request
    ) {
        try {
            adminProductMapper.insertProductDataVersion(
                    request.getVersionCode(),
                    request.getDataDate()
            );
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }

        AdminProductVersionRow created =
                adminProductMapper.selectProductDataVersionByCode(request.getVersionCode());
        return toVersionResponse(created);
    }

    private AdminProductRow fetchSingle(String productType, Long productVersionId) {
        List<AdminProductRow> rows = switch (productType) {
            case "DEPOSIT" -> adminProductMapper.selectDepositProducts(null, productVersionId);
            case "SAVINGS" -> adminProductMapper.selectSavingsProducts(null, productVersionId);
            case "ETF" -> adminProductMapper.selectEtfProducts(null, productVersionId);
            default -> List.of();
        };
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireDataVersion(Long productDataVersionId) {
        if (productDataVersionId == null
                || adminProductMapper.selectProductDataVersion(productDataVersionId) == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
    }

    private String normalizeType(String typeValue) {
        if (typeValue == null || typeValue.isBlank() || typeValue.equalsIgnoreCase("all")) {
            return null;
        }
        String normalized = typeValue.trim().toUpperCase(Locale.ROOT);
        if (!PRODUCT_TYPES.contains(normalized)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        return normalized;
    }

    private AdminProductVersionResponse toVersionResponse(AdminProductVersionRow row) {
        return AdminProductVersionResponse.builder()
                .productDataVersionId(row.getProductDataVersionId())
                .versionCode(row.getVersionCode())
                .dataDate(row.getDataDate())
                .status(row.getStatus())
                .createdAt(row.getCreatedAt())
                .completedAt(row.getCompletedAt())
                .depositCount(row.getDepositCount())
                .savingsCount(row.getSavingsCount())
                .etfCount(row.getEtfCount())
                .build();
    }

    private AdminProductResponse toProductResponse(AdminProductRow row) {
        return AdminProductResponse.builder()
                .productVersionId(row.getProductVersionId())
                .productDataVersionId(row.getProductDataVersionId())
                .productId(row.getProductId())
                .productCode(row.getProductCode())
                .productName(row.getProductName())
                .productType(row.getProductType())
                .description(row.getDescription())
                .productUrl(row.getProductUrl())
                .salesStatus(row.getSalesStatus())
                .createdAt(row.getCreatedAt())
                .minAmount(row.getMinAmount())
                .maxAmount(row.getMaxAmount())
                .minMonth(row.getMinMonth())
                .maxMonth(row.getMaxMonth())
                .minBaseRatePercent(row.getMinBaseRatePercent())
                .maxRatePercent(row.getMaxRatePercent())
                .savingsCategory(row.getSavingsCategory())
                .monthlyMinAmount(row.getMonthlyMinAmount())
                .monthlyMaxAmount(row.getMonthlyMaxAmount())
                .stockCode(row.getStockCode())
                .etfCategory(row.getEtfCategory())
                .trackingIndex(row.getTrackingIndex())
                .annualReturn5yPercent(row.getAnnualReturn5yPercent())
                .bondRatioPercent(row.getBondRatioPercent())
                .riskLevel(row.getRiskLevel())
                .build();
    }
}