package com.example.project.batch.product.etf.service;

import com.example.project.batch.product.etf.domain.ExternalEtfProductMaster;
import com.example.project.batch.product.etf.mapper.EtfMarketDataMapper;
import com.example.project.common.product.RiseEtfPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class EtfProductMasterRegistrationService {

    private final EtfMarketDataMapper mapper;

    @Transactional
    public int registerVerifiedRiseProducts(
            List<ExternalEtfProductMaster> externalProducts,
            Map<String, ExternalEtfProductMaster> officialCatalog
    ) {
        Map<String, ExternalEtfProductMaster> uniqueByStockCode = new LinkedHashMap<>();
        for (ExternalEtfProductMaster product : externalProducts) {
            if (product != null && product.getStockCode() != null) {
                uniqueByStockCode.putIfAbsent(product.getStockCode().trim(), product);
            }
        }

        int registeredCount = 0;
        for (ExternalEtfProductMaster product : uniqueByStockCode.values()) {
            String stockCode = product.getStockCode().trim();
            ExternalEtfProductMaster officialProduct = officialCatalog.get(stockCode);
            if (!isSameVerifiedProduct(product, officialProduct)) {
                log.warn("ETF 상품 마스터 등록 제외: stockCode={}, candidateName={}, officialName={}",
                        stockCode,
                        product.getProductName(),
                        officialProduct == null ? null : officialProduct.getProductName());
                continue;
            }

            if (mapper.selectProductIdByEtfStockCode(stockCode) != null) {
                continue;
            }

            // 저장 직전에 외부 공식 목록의 코드·상품명·운용사를 다시 검증합니다.
            officialProduct = officialCatalog.get(stockCode);
            if (!isSameVerifiedProduct(product, officialProduct)) {
                throw new IllegalStateException(
                        "저장 직전 RISE ETF 공식 검증에 실패했습니다. stockCode=" + stockCode);
            }

            String existingType = mapper.selectProductTypeByCode(stockCode);
            if (existingType != null && !"ETF".equals(existingType)) {
                throw new IllegalStateException(
                        "ETF 종목코드가 다른 상품 유형의 상품코드와 충돌합니다. stockCode=" + stockCode);
            }
            if (existingType == null) {
                mapper.insertEtfProductMaster(stockCode);
                String savedType = mapper.selectProductTypeByCode(stockCode);
                if (!"ETF".equals(savedType)) {
                    throw new IllegalStateException(
                            "ETF 상품 마스터 등록 결과를 확인할 수 없습니다. stockCode=" + stockCode);
                }
                registeredCount++;
            }
        }
        return registeredCount;
    }

    private boolean isSameVerifiedProduct(
            ExternalEtfProductMaster candidate,
            ExternalEtfProductMaster officialProduct
    ) {
        if (candidate == null || officialProduct == null
                || !RiseEtfPolicy.isVerifiedRiseEtf(
                officialProduct.getProductName(), officialProduct.getIssuerName())) {
            return false;
        }
        return normalizeProductName(candidate.getProductName())
                .equals(normalizeProductName(officialProduct.getProductName()));
    }

    private String normalizeProductName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase();
    }
}
