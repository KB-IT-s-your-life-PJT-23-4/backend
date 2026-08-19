package com.example.project.batch.product.etf.service;

import com.example.project.batch.product.etf.client.FscEtfPriceClient;
import com.example.project.batch.product.etf.client.FscEtfProductMasterClient;
import com.example.project.batch.product.etf.client.KrxEtfPriceClient;
import com.example.project.batch.product.etf.domain.EtfHistoryPrice;
import com.example.project.batch.product.etf.domain.EtfMarketDataRefreshResult;
import com.example.project.batch.product.etf.domain.EtfPricePoint;
import com.example.project.batch.product.etf.domain.EtfProductTarget;
import com.example.project.batch.product.etf.domain.EtfReturnMetric;
import com.example.project.batch.product.etf.domain.ExternalEtfPrice;
import com.example.project.batch.product.etf.mapper.EtfMarketDataMapper;
import com.example.project.common.product.RiseEtfPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class EtfMarketDataService {

    private static final int UPSERT_CHUNK_SIZE = 500;
    private static final DateTimeFormatter VERSION_TIME = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FscEtfPriceClient fscClient;
    private final FscEtfProductMasterClient productMasterClient;
    private final KrxEtfPriceClient krxClient;
    private final EtfMarketDataMapper mapper;
    private final EtfMarketDataPersistenceService persistenceService;
    private final EtfAnnualizedReturnCalculator returnCalculator;
    private final EtfProductMasterRegistrationService productMasterRegistrationService;

    @Value("${etf.history.lookback-years:10}")
    private int lookbackYears;

    @Value("${etf.history.start-window-days:14}")
    private int startWindowDays;

    @Value("${etf.history.krx-recent-days:14}")
    private int krxRecentDays;

    @Value("${etf.product-version.publish-enabled:true}")
    private boolean publishEnabled;

    public EtfMarketDataRefreshResult refresh(LocalDate asOfDate) {
        int registeredMasterCount = productMasterRegistrationService.registerVerifiedRiseProducts(
                productMasterClient.fetchRiseCandidates());

        Long sourceDataVersionId = mapper.selectLatestCompletedDataVersionId();
        if (sourceDataVersionId == null) {
            throw new IllegalStateException("ETF 수집 기준이 될 COMPLETED 상품 데이터 버전이 없습니다.");
        }

        List<EtfProductTarget> currentTargets = mapper.selectCurrentEtfTargets();
        List<EtfProductTarget> targets = currentTargets.stream()
                .filter(target -> RiseEtfPolicy.isRiseProductName(target.getProductName()))
                .toList();
        int excludedCount = currentTargets.size() - targets.size();
        if (excludedCount > 0) {
            log.info("RISE 브랜드가 아닌 ETF {}건을 시세 수집 대상에서 제외했습니다.", excludedCount);
        }
        if (targets.isEmpty()) {
            throw new IllegalStateException("수집할 판매 중 RISE ETF 상품이 없습니다.");
        }

        LocalDate historyFrom = asOfDate.minusYears(lookbackYears);
        Map<String, EtfProductTarget> targetByStockCode = new HashMap<>();
        Map<PriceKey, EtfHistoryPrice> priceByKey = new LinkedHashMap<>();
        for (EtfProductTarget target : targets) {
            targetByStockCode.put(target.getStockCode(), target);
            List<ExternalEtfPrice> fscPrices = fscClient.fetchPrices(
                    target.getStockCode(), historyFrom, asOfDate);
            if (fscPrices.isEmpty()) {
                throw new IllegalStateException(
                        "금융위원회 API에서 ETF 시세를 찾지 못했습니다. stockCode=" + target.getStockCode());
            }
            fscPrices.forEach(price -> putPrice(priceByKey, target, price));
        }

        supplementRecentKrxPrices(asOfDate, targetByStockCode, priceByKey);

        List<EtfHistoryPrice> prices = priceByKey.values().stream()
                .sorted(Comparator.comparing(EtfHistoryPrice::getProductId)
                        .thenComparing(EtfHistoryPrice::getBaseDate))
                .toList();
        persistInChunks(prices);

        List<EtfReturnMetric> metrics = calculateMetrics(targets, historyFrom, asOfDate);
        Long publishedDataVersionId = null;
        if (publishEnabled && hasReturnChanges(targets, metrics)) {
            String versionCode = "ETF-" + VERSION_TIME.format(asOfDate) + "-" + System.currentTimeMillis();
            publishedDataVersionId = persistenceService.publishProductDataVersion(
                    sourceDataVersionId, versionCode, asOfDate, metrics);
        }

        log.info("ETF 시세 갱신 완료: 기준일={}, 상품={}건, 종가={}건, 발행버전={}",
                asOfDate, targets.size(), prices.size(), publishedDataVersionId);
        return new EtfMarketDataRefreshResult(
                registeredMasterCount, targets.size(), prices.size(), publishedDataVersionId);
    }

    private boolean hasReturnChanges(
            List<EtfProductTarget> targets,
            List<EtfReturnMetric> metrics
    ) {
        Map<Long, EtfProductTarget> targetByProductId = new HashMap<>();
        targets.forEach(target -> targetByProductId.put(target.getProductId(), target));
        return metrics.stream().anyMatch(metric -> {
            EtfProductTarget target = targetByProductId.get(metric.getProductId());
            return target == null
                    || target.getAnnualReturn10yPercent() == null
                    || target.getAnnualReturn10yPercent()
                    .compareTo(metric.getAnnualReturn10yPercent()) != 0;
        });
    }

    private void supplementRecentKrxPrices(
            LocalDate asOfDate,
            Map<String, EtfProductTarget> targetByStockCode,
            Map<PriceKey, EtfHistoryPrice> priceByKey
    ) {
        LocalDate from = asOfDate.minusDays(Math.max(0, krxRecentDays - 1L));
        for (LocalDate date = from; !date.isAfter(asOfDate); date = date.plusDays(1)) {
            try {
                for (ExternalEtfPrice price : krxClient.fetchPrices(date)) {
                    EtfProductTarget target = targetByStockCode.get(price.getStockCode());
                    if (target != null) {
                        putPrice(priceByKey, target, price);
                    }
                }
            } catch (RuntimeException exception) {
                log.warn("KRX ETF 최근 시세 보강을 건너뜁니다. 기준일={}, 사유={}",
                        date, exception.getMessage());
            }
        }
    }

    private void putPrice(
            Map<PriceKey, EtfHistoryPrice> priceByKey,
            EtfProductTarget target,
            ExternalEtfPrice price
    ) {
        PriceKey key = new PriceKey(target.getProductId(), price.getBaseDate());
        priceByKey.put(key, new EtfHistoryPrice(
                target.getProductId(),
                price.getBaseDate(),
                price.getClosePrice(),
                price.getDataSource()));
    }

    private void persistInChunks(List<EtfHistoryPrice> prices) {
        for (int fromIndex = 0; fromIndex < prices.size(); fromIndex += UPSERT_CHUNK_SIZE) {
            int toIndex = Math.min(fromIndex + UPSERT_CHUNK_SIZE, prices.size());
            persistenceService.upsertPrices(prices.subList(fromIndex, toIndex));
        }
    }

    private List<EtfReturnMetric> calculateMetrics(
            List<EtfProductTarget> targets,
            LocalDate historyFrom,
            LocalDate asOfDate
    ) {
        List<EtfReturnMetric> metrics = new ArrayList<>();
        for (EtfProductTarget target : targets) {
            EtfPricePoint start = mapper.selectFirstPriceInWindow(
                    target.getProductId(), historyFrom, historyFrom.plusDays(startWindowDays));
            EtfPricePoint end = mapper.selectLatestPriceOnOrBefore(target.getProductId(), asOfDate);
            if (start == null || end == null) {
                throw new IllegalStateException(
                        "ETF의 10년 수익률 계산에 필요한 종가가 없습니다. stockCode=" + target.getStockCode());
            }
            metrics.add(new EtfReturnMetric(
                    target.getProductId(),
                    returnCalculator.calculate(start, end),
                    start.getBaseDate(),
                    end.getBaseDate()));
        }
        return metrics;
    }

    private static final class PriceKey {
        private final Long productId;
        private final LocalDate baseDate;

        private PriceKey(Long productId, LocalDate baseDate) {
            this.productId = productId;
            this.baseDate = baseDate;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof PriceKey other)) {
                return false;
            }
            return productId.equals(other.productId) && baseDate.equals(other.baseDate);
        }

        @Override
        public int hashCode() {
            return 31 * productId.hashCode() + baseDate.hashCode();
        }
    }
}
