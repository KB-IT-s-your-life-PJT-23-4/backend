package com.example.project.admin.product.service;

import com.example.project.admin.audit.service.AdminAuditWriter;
import com.example.project.admin.auth.domain.AdminPrincipal;
import com.example.project.admin.product.domain.AdminBaseRateRow;
import com.example.project.admin.product.domain.AdminEtfHoldingRow;
import com.example.project.admin.product.domain.AdminPreferentialRateRow;
import com.example.project.admin.product.domain.AdminProductRow;
import com.example.project.admin.product.domain.AdminProductVersionRow;
import com.example.project.admin.product.dto.request.AdminProductCreateRequest;
import com.example.project.admin.product.dto.request.AdminProductEtfHoldingRequest;
import com.example.project.admin.product.dto.request.AdminProductPreferentialConditionRequest;
import com.example.project.admin.product.dto.request.AdminProductRateTierRequest;
import com.example.project.admin.product.dto.request.AdminProductUpdateRequest;
import com.example.project.admin.product.dto.response.AdminProductEtfHoldingResponse;
import com.example.project.admin.product.dto.response.AdminProductPreferentialConditionResponse;
import com.example.project.admin.product.dto.response.AdminProductRateTierResponse;
import com.example.project.admin.product.dto.response.AdminProductResponse;
import com.example.project.admin.product.dto.response.AdminProductVersionResponse;
import com.example.project.admin.product.mapper.AdminProductMapper;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.common.product.RiseEtfPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final AdminAuditWriter adminAuditWriter;

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
            AdminProductUpdateRequest request,
            AdminPrincipal actor
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
        validateRiseEtf(productType, request.getProductName());

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
                if (request.getAnnualReturn10yPercent() != null) {
                    adminProductMapper.updateEtfReturn(productVersionId, request.getAnnualReturn10yPercent());
                }
            }
        }

        applyRateTiers(productType, productVersionId, request.getRateTiers(), request.getRateBaseDate());
        applyPreferentialConditions(productType, productVersionId, request.getPreferentialConditions());
        applyEtfHoldings(productType, productVersionId, request.getEtfHoldings());

        AdminProductResponse response = toProductResponse(fetchSingle(productType, productVersionId));

        adminAuditWriter.record(
                actor,
                "PRODUCT_UPDATE",
                "PRODUCT",
                productVersionId,
                "상품 정보를 수정했습니다.",
                Map.of(
                        "productDataVersionId", productDataVersionId,
                        "productType", productType,
                        "productName", response.getProductName(),
                        "salesStatus", response.getSalesStatus()
                )
        );

        return response;
    }

    @Transactional
    public AdminProductResponse createProduct(
            Long productDataVersionId,
            AdminProductCreateRequest request,
            AdminPrincipal actor
    ) {
        AdminProductVersionRow version = requireDataVersion(productDataVersionId);
        if (!"LOADING".equals(version.getStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        String type = normalizeType(request.getProductType());
        if (type == null) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        validateRiseEtf(type, request.getProductName());

        Long productId = adminProductMapper.selectProductIdByCode(request.getProductCode());
        if (productId == null) {
            try {
                adminProductMapper.insertProduct(request.getProductCode(), type);
            } catch (DuplicateKeyException exception) {
                throw new ServiceException(ResponseCode.DUPLICATE_DATA);
            }
            productId = adminProductMapper.selectLastInsertedId();
        } else {
            String existingType = adminProductMapper.selectProductTypeByCode(request.getProductCode());
            if (!type.equals(existingType)
                    || adminProductMapper.countProductVersionInDataVersion(
                    productDataVersionId, productId) > 0) {
                throw new ServiceException(ResponseCode.DUPLICATE_DATA);
            }
        }

        adminProductMapper.insertProductVersion(
                productDataVersionId, productId, request.getProductName(),
                request.getDescription(), request.getProductUrl(), request.getSalesStatus());
        Long productVersionId = adminProductMapper.selectLastInsertedId();

        switch (type) {
            case "DEPOSIT" -> {
                adminProductMapper.insertDeposit(productVersionId, request.getMinAmount(),
                        request.getMaxAmount(), request.getMinMonth(), request.getMaxMonth());
                insertRateTiers(productVersionId, request.getRateTiers(), request.getRateBaseDate());
                insertPreferentialConditions(productVersionId, request.getPreferentialConditions());
            }
            case "SAVINGS" -> {
                adminProductMapper.insertSavings(productVersionId, request.getSavingsCategory(),
                        request.getMonthlyMinAmount(), request.getMonthlyMaxAmount(),
                        request.getMinMonth(), request.getMaxMonth());
                insertRateTiers(productVersionId, request.getRateTiers(), request.getRateBaseDate());
                insertPreferentialConditions(productVersionId, request.getPreferentialConditions());
            }
            case "ETF" -> {
                adminProductMapper.insertEtf(productVersionId, request.getStockCode(),
                        request.getEtfCategory(), request.getTrackingIndex(),
                        request.getAnnualReturn10yPercent(), request.getBondRatioPercent(),
                        request.getRiskLevel());
                insertEtfHoldings(productVersionId, request.getEtfHoldings());
            }
        }

        AdminProductResponse response = toProductResponse(fetchSingle(type, productVersionId));

        adminAuditWriter.record(
                actor,
                "PRODUCT_CREATE",
                "PRODUCT",
                productVersionId,
                "상품을 생성했습니다.",
                Map.of(
                        "productDataVersionId", productDataVersionId,
                        "productType", type,
                        "productName", response.getProductName(),
                        "salesStatus", response.getSalesStatus()
                )
        );

        return response;
    }

    @Transactional
    public AdminProductVersionResponse createDraftVersionFromLatest(AdminPrincipal actor) {
        List<AdminProductVersionRow> allVersions = adminProductMapper.selectProductDataVersions();
        if (!allVersions.isEmpty() && "LOADING".equals(allVersions.get(0).getStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        String versionCode = generateNextVersionCode();
        adminProductMapper.insertProductDataVersion(versionCode, LocalDate.now());
        Long newDataVersionId = adminProductMapper.selectLastInsertedId();

        AdminProductVersionRow latestCompleted = adminProductMapper.selectLatestCompletedDataVersion();
        if (latestCompleted != null) {
            cloneProducts(latestCompleted.getProductDataVersionId(), newDataVersionId);
        }

        AdminProductVersionResponse response = toVersionResponse(
                adminProductMapper.selectProductDataVersion(newDataVersionId)
        );

        adminAuditWriter.record(
                actor,
                "PRODUCT_VERSION_CREATE",
                "PRODUCT_DATA_VERSION",
                newDataVersionId,
                "상품 데이터 초안 버전을 생성했습니다.",
                Map.of("versionCode", response.getVersionCode())
        );

        return response;
    }

    @Transactional
    public AdminProductVersionResponse completeProductDataVersion(
            Long productDataVersionId,
            AdminPrincipal actor
    ) {
        AdminProductVersionRow version = requireDataVersion(productDataVersionId);
        if (!"LOADING".equals(version.getStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }
        adminProductMapper.completeProductDataVersion(productDataVersionId);
        AdminProductVersionResponse response = toVersionResponse(
                adminProductMapper.selectProductDataVersion(productDataVersionId)
        );

        adminAuditWriter.record(
                actor,
                "PRODUCT_VERSION_COMPLETE",
                "PRODUCT_DATA_VERSION",
                productDataVersionId,
                "상품 데이터 버전을 확정했습니다.",
                Map.of("versionCode", response.getVersionCode())
        );

        return response;
    }

    @Transactional
    public void deleteProductDataVersion(
            Long productDataVersionId,
            AdminPrincipal actor
    ) {
        AdminProductVersionRow version = requireDataVersion(productDataVersionId);

        adminProductMapper.deleteEtfHoldingsByDataVersion(productDataVersionId);
        adminProductMapper.deletePreferentialRatesByDataVersion(productDataVersionId);
        adminProductMapper.deleteBaseRatesByDataVersion(productDataVersionId);
        adminProductMapper.deleteEtfByDataVersion(productDataVersionId);
        adminProductMapper.deleteDepositByDataVersion(productDataVersionId);
        adminProductMapper.deleteSavingsByDataVersion(productDataVersionId);
        adminProductMapper.deleteProductVersionsByDataVersion(productDataVersionId);
        adminProductMapper.deleteProductDataVersion(productDataVersionId);

        adminAuditWriter.record(
                actor,
                "PRODUCT_VERSION_DELETE",
                "PRODUCT_DATA_VERSION",
                productDataVersionId,
                "상품 데이터 버전을 삭제했습니다.",
                Map.of("versionCode", version.getVersionCode())
        );
    }

    private void cloneProducts(Long sourceDataVersionId, Long targetDataVersionId) {
        for (AdminProductRow row : adminProductMapper.selectDepositProducts(sourceDataVersionId, null)) {
            Long newVersionId = cloneProductVersion(targetDataVersionId, row);
            adminProductMapper.insertDeposit(newVersionId, row.getMinAmount(), row.getMaxAmount(),
                    row.getMinMonth(), row.getMaxMonth());
            cloneBaseRates(row.getProductVersionId(), newVersionId);
            clonePreferentialRates(row.getProductVersionId(), newVersionId);
        }
        for (AdminProductRow row : adminProductMapper.selectSavingsProducts(sourceDataVersionId, null)) {
            Long newVersionId = cloneProductVersion(targetDataVersionId, row);
            adminProductMapper.insertSavings(newVersionId, row.getSavingsCategory(),
                    row.getMonthlyMinAmount(), row.getMonthlyMaxAmount(),
                    row.getMinMonth(), row.getMaxMonth());
            cloneBaseRates(row.getProductVersionId(), newVersionId);
            clonePreferentialRates(row.getProductVersionId(), newVersionId);
        }
        for (AdminProductRow row : adminProductMapper.selectEtfProducts(sourceDataVersionId, null)) {
            if (!RiseEtfPolicy.isRiseProductName(row.getProductName())) {
                continue;
            }
            Long newVersionId = cloneProductVersion(targetDataVersionId, row);
            adminProductMapper.insertEtf(newVersionId, row.getStockCode(), row.getEtfCategory(),
                    row.getTrackingIndex(), row.getAnnualReturn10yPercent(),
                    row.getBondRatioPercent(), row.getRiskLevel());
            cloneEtfHoldings(row.getProductVersionId(), newVersionId);
        }
    }

    private Long cloneProductVersion(Long targetDataVersionId, AdminProductRow row) {
        adminProductMapper.insertProductVersion(
                targetDataVersionId, row.getProductId(), row.getProductName(),
                row.getDescription(), row.getProductUrl(), row.getSalesStatus());
        return adminProductMapper.selectLastInsertedId();
    }

    private void cloneBaseRates(Long sourceProductVersionId, Long targetProductVersionId) {
        for (AdminBaseRateRow tier : adminProductMapper.selectBaseRateTiers(sourceProductVersionId)) {
            adminProductMapper.insertBaseInterestRate(
                    targetProductVersionId, tier.getMinMonth(), tier.getMaxMonth(),
                    tier.getBaseRatePercent(), tier.getMaxRatePercent(), tier.getBaseDate());
        }
    }

    private void clonePreferentialRates(Long sourceProductVersionId, Long targetProductVersionId) {
        for (AdminPreferentialRateRow row : adminProductMapper.selectPreferentialRates(sourceProductVersionId)) {
            adminProductMapper.insertPreferentialRate(
                    targetProductVersionId, row.getAdditionalRatePercent(),
                    row.getConditionCode(), row.getPreferentialCondition(), row.getBaseDate());
        }
    }

    private void cloneEtfHoldings(Long sourceProductVersionId, Long targetProductVersionId) {
        for (AdminEtfHoldingRow row : adminProductMapper.selectEtfHoldings(sourceProductVersionId)) {
            adminProductMapper.insertEtfHolding(
                    targetProductVersionId, row.getHoldingRank(), row.getHoldingName(),
                    row.getHoldingCode(), row.getAssetType(), row.getCountryCode(),
                    row.getWeightPercent(), row.getBaseDate());
        }
    }

    private void insertRateTiers(Long productVersionId, List<AdminProductRateTierRequest> tiers, LocalDate rateBaseDate) {
        if (tiers == null) return;
        LocalDate baseDate = rateBaseDate != null ? rateBaseDate : LocalDate.now();
        for (AdminProductRateTierRequest tier : tiers) {
            validateTierRange(tier);
            adminProductMapper.insertBaseInterestRate(
                    productVersionId,
                    tier.getMinMonth(),
                    tier.getMaxMonth(),
                    tier.getBaseRatePercent(),
                    tier.getMaxRatePercent(),
                    baseDate);
        }
    }

    private void insertPreferentialConditions(
            Long productVersionId, List<AdminProductPreferentialConditionRequest> conditions
    ) {
        if (conditions == null) return;
        for (AdminProductPreferentialConditionRequest condition : conditions) {
            LocalDate baseDate = condition.getBaseDate() != null ? condition.getBaseDate() : LocalDate.now();
            try {
                adminProductMapper.insertPreferentialRate(
                        productVersionId, condition.getAdditionalRatePercent(),
                        condition.getConditionCode(), condition.getPreferentialCondition(), baseDate);
            } catch (DuplicateKeyException exception) {
                throw new ServiceException(ResponseCode.DUPLICATE_DATA);
            }
        }
    }

    private void insertEtfHoldings(Long productVersionId, List<AdminProductEtfHoldingRequest> holdings) {
        if (holdings == null) return;
        for (AdminProductEtfHoldingRequest holding : holdings) {
            LocalDate baseDate = holding.getBaseDate() != null ? holding.getBaseDate() : LocalDate.now();
            try {
                adminProductMapper.insertEtfHolding(
                        productVersionId, holding.getHoldingRank(), holding.getHoldingName(),
                        holding.getHoldingCode(), holding.getAssetType(), holding.getCountryCode(),
                        holding.getWeightPercent(), baseDate);
            } catch (DuplicateKeyException exception) {
                throw new ServiceException(ResponseCode.DUPLICATE_DATA);
            }
        }
    }

    private void applyRateTiers(
            String productType, Long productVersionId, List<AdminProductRateTierRequest> tiers, LocalDate rateBaseDate
    ) {
        if (!"DEPOSIT".equals(productType) && !"SAVINGS".equals(productType)) return;
        if (tiers == null) return;

        LocalDate baseDate = rateBaseDate != null ? rateBaseDate : LocalDate.now();
        List<AdminBaseRateRow> current = adminProductMapper.selectBaseRateTiers(productVersionId);
        Set<Long> incomingIds = tiers.stream()
                .map(AdminProductRateTierRequest::getBaseInterestRateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (AdminBaseRateRow row : current) {
            if (!incomingIds.contains(row.getBaseInterestRateId())) {
                adminProductMapper.deleteBaseInterestRate(row.getBaseInterestRateId());
            }
        }

        for (AdminProductRateTierRequest tier : tiers) {
            validateTierRange(tier);
            if (tier.getBaseInterestRateId() != null) {
                adminProductMapper.updateBaseInterestRate(
                        tier.getBaseInterestRateId(),
                        tier.getMinMonth(),
                        tier.getMaxMonth(),
                        tier.getBaseRatePercent(),
                        tier.getMaxRatePercent(),
                        baseDate);
            } else {
                adminProductMapper.insertBaseInterestRate(
                        productVersionId,
                        tier.getMinMonth(),
                        tier.getMaxMonth(),
                        tier.getBaseRatePercent(),
                        tier.getMaxRatePercent(),
                        baseDate);
            }
        }
    }

    private void applyPreferentialConditions(
            String productType, Long productVersionId, List<AdminProductPreferentialConditionRequest> conditions
    ) {
        if (!"DEPOSIT".equals(productType) && !"SAVINGS".equals(productType)) return;
        if (conditions == null) return;

        List<AdminPreferentialRateRow> current = adminProductMapper.selectPreferentialRates(productVersionId);
        Set<Long> keepIds = conditions.stream()
                .map(AdminProductPreferentialConditionRequest::getPreferentialInterestRateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (AdminPreferentialRateRow row : current) {
            if (!keepIds.contains(row.getPreferentialInterestRateId())) {
                adminProductMapper.deletePreferentialRate(row.getPreferentialInterestRateId());
            }
        }
        for (AdminProductPreferentialConditionRequest condition : conditions) {
            LocalDate baseDate = condition.getBaseDate() != null ? condition.getBaseDate() : LocalDate.now();
            try {
                if (condition.getPreferentialInterestRateId() != null) {
                    adminProductMapper.updatePreferentialRate(
                            condition.getPreferentialInterestRateId(), condition.getAdditionalRatePercent(),
                            condition.getConditionCode(), condition.getPreferentialCondition(), baseDate);
                } else {
                    adminProductMapper.insertPreferentialRate(
                            productVersionId, condition.getAdditionalRatePercent(),
                            condition.getConditionCode(), condition.getPreferentialCondition(), baseDate);
                }
            } catch (DuplicateKeyException exception) {
                throw new ServiceException(ResponseCode.DUPLICATE_DATA);
            }
        }
    }

    private void applyEtfHoldings(String productType, Long productVersionId, List<AdminProductEtfHoldingRequest> holdings) {
        if (!"ETF".equals(productType)) return;
        if (holdings == null) return;

        List<AdminEtfHoldingRow> current = adminProductMapper.selectEtfHoldings(productVersionId);
        Set<Long> keepIds = holdings.stream()
                .map(AdminProductEtfHoldingRequest::getHoldingId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (AdminEtfHoldingRow row : current) {
            if (!keepIds.contains(row.getHoldingId())) {
                adminProductMapper.deleteEtfHolding(row.getHoldingId());
            }
        }
        for (AdminProductEtfHoldingRequest holding : holdings) {
            LocalDate baseDate = holding.getBaseDate() != null ? holding.getBaseDate() : LocalDate.now();
            try {
                if (holding.getHoldingId() != null) {
                    adminProductMapper.updateEtfHolding(
                            holding.getHoldingId(), holding.getHoldingRank(), holding.getHoldingName(),
                            holding.getHoldingCode(), holding.getAssetType(), holding.getCountryCode(),
                            holding.getWeightPercent(), baseDate);
                } else {
                    adminProductMapper.insertEtfHolding(
                            productVersionId, holding.getHoldingRank(), holding.getHoldingName(),
                            holding.getHoldingCode(), holding.getAssetType(), holding.getCountryCode(),
                            holding.getWeightPercent(), baseDate);
                }
            } catch (DuplicateKeyException exception) {
                throw new ServiceException(ResponseCode.DUPLICATE_DATA);
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

    private void validateRiseEtf(String productType, String productName) {
        if ("ETF".equals(productType) && !RiseEtfPolicy.isRiseProductName(productName)) {
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
        boolean isEtf = "ETF".equals(row.getProductType());

        List<AdminProductRateTierResponse> tiers = isEtf
                ? List.of()
                : adminProductMapper.selectBaseRateTiers(row.getProductVersionId()).stream()
                .map(tier -> AdminProductRateTierResponse.builder()
                        .baseInterestRateId(tier.getBaseInterestRateId())
                        .minMonth(tier.getMinMonth())
                        .maxMonth(tier.getMaxMonth())
                        .baseRatePercent(tier.getBaseRatePercent())
                        .maxRatePercent(tier.getMaxRatePercent())
                        .baseDate(tier.getBaseDate())
                        .build())
                .toList();

        List<AdminProductPreferentialConditionResponse> preferentialConditions = isEtf
                ? List.of()
                : adminProductMapper.selectPreferentialRates(row.getProductVersionId()).stream()
                .map(condition -> AdminProductPreferentialConditionResponse.builder()
                        .preferentialInterestRateId(condition.getPreferentialInterestRateId())
                        .additionalRatePercent(condition.getAdditionalRatePercent())
                        .conditionCode(condition.getConditionCode())
                        .preferentialCondition(condition.getPreferentialCondition())
                        .baseDate(condition.getBaseDate())
                        .build())
                .toList();

        List<AdminProductEtfHoldingResponse> etfHoldings = isEtf
                ? adminProductMapper.selectEtfHoldings(row.getProductVersionId()).stream()
                .map(holding -> AdminProductEtfHoldingResponse.builder()
                        .holdingId(holding.getHoldingId())
                        .holdingRank(holding.getHoldingRank())
                        .holdingName(holding.getHoldingName())
                        .holdingCode(holding.getHoldingCode())
                        .assetType(holding.getAssetType())
                        .countryCode(holding.getCountryCode())
                        .weightPercent(holding.getWeightPercent())
                        .baseDate(holding.getBaseDate())
                        .build())
                .toList()
                : List.of();

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
                .preferentialConditions(preferentialConditions)
                .savingsCategory(row.getSavingsCategory())
                .monthlyMinAmount(row.getMonthlyMinAmount())
                .monthlyMaxAmount(row.getMonthlyMaxAmount())
                .stockCode(row.getStockCode())
                .etfCategory(row.getEtfCategory())
                .trackingIndex(row.getTrackingIndex())
                .annualReturn10yPercent(row.getAnnualReturn10yPercent())
                .bondRatioPercent(row.getBondRatioPercent())
                .riskLevel(row.getRiskLevel())
                .etfHoldings(etfHoldings)
                .build();
    }
}
