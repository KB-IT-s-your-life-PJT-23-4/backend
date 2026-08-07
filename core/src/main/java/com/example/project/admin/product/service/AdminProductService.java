package com.example.project.admin.product.service;

import com.example.project.admin.product.domain.AdminBaseRateRow;
import com.example.project.admin.product.domain.AdminProductRow;
import com.example.project.admin.product.domain.AdminProductVersionRow;
import com.example.project.admin.product.dto.request.AdminProductCreateRequest;
import com.example.project.admin.product.dto.request.AdminProductRateTierRequest;
import com.example.project.admin.product.dto.request.AdminProductUpdateRequest;
import com.example.project.admin.product.dto.response.AdminProductRateTierResponse;
import com.example.project.admin.product.dto.response.AdminProductResponse;
import com.example.project.admin.product.dto.response.AdminProductVersionResponse;
import com.example.project.admin.product.mapper.AdminProductMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
                productVersionId, request.getProductName(), request.getDescription(),
                request.getProductUrl(), request.getSalesStatus());
        if (updated == 0) {
            throw new ServiceException(ResponseCode.PRODUCT_NOT_FOUND);
        }

        switch (productType) {
            case "DEPOSIT" -> adminProductMapper.updateDepositDetail(
                    productVersionId, request.getMinAmount(), request.getMaxAmount(),
                    request.getMinMonth(), request.getMaxMonth());
            case "SAVINGS" -> adminProductMapper.updateSavingsDetail(
                    productVersionId, request.getSavingsCategory(),
                    request.getMonthlyMinAmount(), request.getMonthlyMaxAmount(),
                    request.getMinMonth(), request.getMaxMonth());
            case "ETF" -> {
                adminProductMapper.updateEtfDetail(
                        productVersionId, request.getStockCode(), request.getEtfCategory(),
                        request.getTrackingIndex(), request.getBondRatioPercent(), request.getRiskLevel());
                if (request.getAnnualReturn5yPercent() != null) {
                    adminProductMapper.updateEtfReturn(productVersionId, request.getAnnualReturn5yPercent());
                }
            }
        }

        applyRateTiers(productType, existing.getProductVersionId(), request.getRateTiers());

        return toProductResponse(fetchSingle(productType, productVersionId));
    }

    @Transactional
    public AdminProductResponse createProduct(
            Long productDataVersionId,
            AdminProductCreateRequest request
    ) {
        AdminProductVersionRow version = requireDataVersion(productDataVersionId);
        if (!"LOADING".equals(version.getStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        String type = normalizeType(request.getProductType());
        if (type == null) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        try {
            adminProductMapper.insertProduct(request.getProductCode(), type);
        } catch (DuplicateKeyException exception) {
            throw new ServiceException(ResponseCode.DUPLICATE_DATA);
        }
        Long productId = adminProductMapper.selectLastInsertedId();

        adminProductMapper.insertProductVersion(
                productDataVersionId, productId, request.getProductName(),
                request.getDescription(), request.getProductUrl(), request.getSalesStatus());
        Long productVersionId = adminProductMapper.selectLastInsertedId();

        switch (type) {
            case "DEPOSIT" -> {
                adminProductMapper.insertDeposit(productVersionId, request.getMinAmount(),
                        request.getMaxAmount(), request.getMinMonth(), request.getMaxMonth());
                insertRateTiers(productVersionId, request.getRateTiers());
            }
            case "SAVINGS" -> {
                adminProductMapper.insertSavings(productVersionId, request.getSavingsCategory(),
                        request.getMonthlyMinAmount(), request.getMonthlyMaxAmount(),
                        request.getMinMonth(), request.getMaxMonth());
                insertRateTiers(productVersionId, request.getRateTiers());
            }
            case "ETF" -> adminProductMapper.insertEtf(productVersionId, request.getStockCode(),
                    request.getEtfCategory(), request.getTrackingIndex(),
                    request.getAnnualReturn5yPercent(), request.getBondRatioPercent(),
                    request.getRiskLevel());
        }

        return toProductResponse(fetchSingle(type, productVersionId));
    }

    @Transactional
    public AdminProductVersionResponse createDraftVersionFromLatest() {
        String versionCode = generateNextVersionCode();
        adminProductMapper.insertProductDataVersion(versionCode, LocalDate.now());
        AdminProductVersionRow created = adminProductMapper.selectProductDataVersionByCode(versionCode);

        AdminProductVersionRow latestCompleted = adminProductMapper.selectLatestCompletedDataVersion();
        if (latestCompleted != null) {
            cloneProducts(latestCompleted.getProductDataVersionId(), created.getProductDataVersionId());
        }

        return toVersionResponse(
                adminProductMapper.selectProductDataVersion(created.getProductDataVersionId()));
    }

    @Transactional
    public AdminProductVersionResponse completeProductDataVersion(Long productDataVersionId) {
        AdminProductVersionRow version = requireDataVersion(productDataVersionId);
        if (!"LOADING".equals(version.getStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }
        adminProductMapper.completeProductDataVersion(productDataVersionId);
        return toVersionResponse(adminProductMapper.selectProductDataVersion(productDataVersionId));
    }

    private void cloneProducts(Long sourceDataVersionId, Long targetDataVersionId) {
        for (AdminProductRow row : adminProductMapper.selectDepositProducts(sourceDataVersionId, null)) {
            Long newVersionId = cloneProductVersion(targetDataVersionId, row);
            adminProductMapper.insertDeposit(newVersionId, row.getMinAmount(), row.getMaxAmount(),
                    row.getMinMonth(), row.getMaxMonth());
            cloneBaseRates(row.getProductVersionId(), newVersionId);
        }
        for (AdminProductRow row : adminProductMapper.selectSavingsProducts(sourceDataVersionId, null)) {
            Long newVersionId = cloneProductVersion(targetDataVersionId, row);
            adminProductMapper.insertSavings(newVersionId, row.getSavingsCategory(),
                    row.getMonthlyMinAmount(), row.getMonthlyMaxAmount(),
                    row.getMinMonth(), row.getMaxMonth());
            cloneBaseRates(row.getProductVersionId(), newVersionId);
        }
        for (AdminProductRow row : adminProductMapper.selectEtfProducts(sourceDataVersionId, null)) {
            Long newVersionId = cloneProductVersion(targetDataVersionId, row);
            adminProductMapper.insertEtf(newVersionId, row.getStockCode(), row.getEtfCategory(),
                    row.getTrackingIndex(), row.getAnnualReturn5yPercent(),
                    row.getBondRatioPercent(), row.getRiskLevel());
        }
    }

    private Long cloneProductVersion(Long targetDataVersionId, AdminProductRow row) {
        adminProductMapper.insertProductVersion(
                targetDataVersionId, row.getProductId(), row.getProductName(),
                row.getDescription(), row.getProductUrl(), row.getSalesStatus());
        return adminProductMapper.selectLastInsertedId();
    }

    private void cloneBaseRates(Long sourceProductVersionId, Long targetProductVersionId) {
        List<AdminBaseRateRow> tiers = adminProductMapper.selectBaseRateTiers(sourceProductVersionId);
        for (AdminBaseRateRow tier : tiers) {
            adminProductMapper.insertBaseInterestRate(
                    targetProductVersionId, tier.getMinMonth(), tier.getMaxMonth(),
                    tier.getBaseRatePercent(), tier.getMaxRatePercent(), tier.getBaseDate());
        }
    }

    private void insertRateTiers(Long productVersionId, List<AdminProductRateTierRequest> rateTiers) {
        if (rateTiers == null) return;
        for (AdminProductRateTierRequest tier : rateTiers) {
            validateTierRange(tier);
            adminProductMapper.insertBaseInterestRate(
                    productVersionId, tier.getMinMonth(), tier.getMaxMonth(),
                    tier.getBaseRatePercent(), tier.getMaxRatePercent(), LocalDate.now());
        }
    }

    /**
     * 요청으로 온 rateTiers 목록을 DB의 현재 구간 목록과 비교해서
     * id가 있는 건 수정, id가 없는 건 추가, 요청에 없는 기존 구간은 삭제한다.
     */
    private void applyRateTiers(String productType, Long productVersionId, List<AdminProductRateTierRequest> rateTiers) {
        if (!"DEPOSIT".equals(productType) && !"SAVINGS".equals(productType)) return;
        if (rateTiers == null) return; // 요청에 아예 없으면 금리는 건드리지 않는다

        List<AdminBaseRateRow> current = adminProductMapper.selectBaseRateTiers(productVersionId);
        Set<Long> keepIds = rateTiers.stream()
                .map(AdminProductRateTierRequest::getBaseInterestRateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (AdminBaseRateRow row : current) {
            if (!keepIds.contains(row.getBaseInterestRateId())) {
                adminProductMapper.deleteBaseInterestRate(row.getBaseInterestRateId());
            }
        }

        for (AdminProductRateTierRequest tier : rateTiers) {
            validateTierRange(tier);
            if (tier.getBaseInterestRateId() != null) {
                adminProductMapper.updateBaseInterestRate(
                        tier.getBaseInterestRateId(), tier.getMinMonth(), tier.getMaxMonth(),
                        tier.getBaseRatePercent(), tier.getMaxRatePercent());
            } else {
                adminProductMapper.insertBaseInterestRate(
                        productVersionId, tier.getMinMonth(), tier.getMaxMonth(),
                        tier.getBaseRatePercent(), tier.getMaxRatePercent(), LocalDate.now());
            }
        }
    }

    private void validateTierRange(AdminProductRateTierRequest tier) {
        if (tier.getMinMonth() == null || tier.getMinMonth() <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        if (tier.getMaxMonth() != null && tier.getMaxMonth() < tier.getMinMonth()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        if (tier.getMaxRatePercent().compareTo(tier.getBaseRatePercent()) < 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
    }

    private String generateNextVersionCode() {
        LocalDate today = LocalDate.now();
        String prefix = String.format("%04d.%02d-v", today.getYear(), today.getMonthValue());
        List<String> existing = adminProductMapper.selectVersionCodesByPrefix(prefix);

        int maxSuffix = 0;
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "(\\d+)$");
        for (String code : existing) {
            Matcher matcher = pattern.matcher(code);
            if (matcher.matches()) {
                maxSuffix = Math.max(maxSuffix, Integer.parseInt(matcher.group(1)));
            }
        }
        return prefix + (maxSuffix + 1);
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

    private AdminProductVersionRow requireDataVersion(Long productDataVersionId) {
        AdminProductVersionRow version = productDataVersionId == null
                ? null
                : adminProductMapper.selectProductDataVersion(productDataVersionId);
        if (version == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        return version;
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
        List<AdminProductRateTierResponse> tiers = "ETF".equals(row.getProductType())
                ? List.of()
                : adminProductMapper.selectBaseRateTiers(row.getProductVersionId()).stream()
                .map(tier -> AdminProductRateTierResponse.builder()
                        .baseInterestRateId(tier.getBaseInterestRateId())
                        .minMonth(tier.getMinMonth())
                        .maxMonth(tier.getMaxMonth())
                        .baseRatePercent(tier.getBaseRatePercent())
                        .maxRatePercent(tier.getMaxRatePercent())
                        .build())
                .toList();

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
                .rateTiers(tiers)
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