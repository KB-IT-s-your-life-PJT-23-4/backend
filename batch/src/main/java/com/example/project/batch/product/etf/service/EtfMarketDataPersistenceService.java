package com.example.project.batch.product.etf.service;

import com.example.project.batch.product.etf.domain.EtfHistoryPrice;
import com.example.project.batch.product.etf.domain.EtfReturnMetric;
import com.example.project.batch.product.etf.mapper.EtfMarketDataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EtfMarketDataPersistenceService {

    private final EtfMarketDataMapper mapper;

    @Transactional
    public void upsertPrices(List<EtfHistoryPrice> prices) {
        if (!prices.isEmpty()) {
            mapper.upsertHistoryPrices(prices);
        }
    }

    @Transactional
    public Long publishProductDataVersion(
            Long sourceDataVersionId,
            String versionCode,
            LocalDate dataDate,
            List<EtfReturnMetric> metrics
    ) {
        if (mapper.countLoadingDataVersions() > 0) {
            throw new IllegalStateException(
                    "LOADING 상태의 상품 데이터 버전이 있어 ETF 수익률 버전을 발행할 수 없습니다.");
        }

        mapper.insertProductDataVersion(versionCode, dataDate);
        Long targetDataVersionId = mapper.selectLastInsertedId();
        if (targetDataVersionId == null) {
            throw new IllegalStateException("ETF 상품 데이터 버전 ID를 생성하지 못했습니다.");
        }

        mapper.cloneProductVersions(sourceDataVersionId, targetDataVersionId);
        mapper.cloneDeposits(sourceDataVersionId, targetDataVersionId);
        mapper.cloneSavings(sourceDataVersionId, targetDataVersionId);
        mapper.cloneEtfs(sourceDataVersionId, targetDataVersionId);
        mapper.cloneBaseInterestRates(sourceDataVersionId, targetDataVersionId);
        mapper.clonePreferentialInterestRates(sourceDataVersionId, targetDataVersionId);
        mapper.cloneEtfHoldings(sourceDataVersionId, targetDataVersionId);

        for (EtfReturnMetric metric : metrics) {
            int updated = mapper.updateEtfAnnualReturn(
                    targetDataVersionId,
                    metric.getProductId(),
                    metric.getAnnualReturn10yPercent());
            if (updated != 1) {
                throw new IllegalStateException(
                        "ETF 10년 연환산 수익률을 새 상품 버전에 반영하지 못했습니다. productId="
                                + metric.getProductId());
            }
        }

        if (mapper.completeProductDataVersion(targetDataVersionId) != 1) {
            throw new IllegalStateException("ETF 상품 데이터 버전을 COMPLETED로 전환하지 못했습니다.");
        }
        return targetDataVersionId;
    }
}
