package com.example.project.batch.product.etf.client;

import com.example.project.batch.product.etf.domain.ExternalEtfProductMaster;
import com.example.project.common.product.RiseEtfPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FscEtfProductMasterClient {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RetryingHttpRequester requester;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final int pageSize;
    private final int lookbackDays;

    public FscEtfProductMasterClient(
            RetryingHttpRequester requester,
            @Value("${fsc.etf-master.base-url:${fsc.etf.base-url}}") String baseUrl,
            @Value("${fsc.etf-master.api-key:${fsc.etf.api-key}}") String apiKey,
            @Value("${etf.api.page-size:1000}") int pageSize,
            @Value("${etf.master.lookback-days:14}") int lookbackDays
    ) {
        this.requester = requester;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.pageSize = pageSize;
        this.lookbackDays = Math.max(1, lookbackDays);
    }

    /**
     * 금융위원회 ETF 시세에서 기준일 직전의 RISE 종목 후보를 수집합니다.
     * 시세 응답에는 운용사가 없으므로 이 결과만으로 상품을 등록해서는 안 됩니다.
     */
    public List<ExternalEtfProductMaster> fetchRiseCandidates(LocalDate asOfDate) {
        LocalDate from = asOfDate.minusDays(lookbackDays - 1L);
        Map<String, ExternalEtfProductMaster> latestByStockCode = new LinkedHashMap<>();
        int page = 1;
        int totalCount;
        do {
            String body = requester.get(
                    buildUri(from, asOfDate.plusDays(1), page),
                    Map.of(),
                    "금융위원회 ETF 시세");
            ParsedPage parsedPage = parsePage(body);
            for (ExternalEtfProductMaster item : parsedPage.getItems()) {
                latestByStockCode.putIfAbsent(item.getStockCode(), item);
            }
            totalCount = parsedPage.getTotalCount();
            page++;
        } while ((long) (page - 1) * pageSize < totalCount);

        if (latestByStockCode.isEmpty()) {
            throw new IllegalStateException("금융위원회 ETF 시세에서 최근 RISE 상품 후보를 찾지 못했습니다.");
        }
        return new ArrayList<>(latestByStockCode.values());
    }

    ParsedPage parsePage(String body) {
        try {
            JsonNode response = objectMapper.readTree(body).path("response");
            JsonNode header = response.path("header");
            String resultCode = header.path("resultCode").asText();
            if (!resultCode.isBlank() && !"00".equals(resultCode)) {
                throw new IllegalStateException("금융위원회 ETF 시세 API 오류: "
                        + header.path("resultMsg").asText("알 수 없는 오류"));
            }

            JsonNode responseBody = response.path("body");
            int totalCount = responseBody.path("totalCount").asInt(0);
            JsonNode itemNode = responseBody.path("items").path("item");
            List<ExternalEtfProductMaster> items = new ArrayList<>();
            if (itemNode.isArray()) {
                itemNode.forEach(item -> addItem(items, item));
            } else if (itemNode.isObject()) {
                addItem(items, itemNode);
            }
            return new ParsedPage(totalCount, items);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("금융위원회 ETF 시세 응답을 해석할 수 없습니다.", exception);
        }
    }

    private void addItem(List<ExternalEtfProductMaster> items, JsonNode item) {
        String stockCode = item.path("srtnCd").asText("").trim();
        String productName = item.path("itmsNm").asText("").trim();
        if (stockCode.isBlank() || !RiseEtfPolicy.isRiseProductName(productName)) {
            return;
        }
        items.add(new ExternalEtfProductMaster(stockCode, productName, null));
    }

    private URI buildUri(LocalDate from, LocalDate exclusiveTo, int page) {
        String query = "serviceKey=" + apiKey
                + "&resultType=json"
                + "&numOfRows=" + pageSize
                + "&pageNo=" + page
                + "&beginBasDt=" + BASIC_DATE.format(from)
                + "&endBasDt=" + BASIC_DATE.format(exclusiveTo)
                + "&likeItmsNm=" + encode("RISE");
        return URI.create(baseUrl + "?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static final class ParsedPage {
        private final int totalCount;
        private final List<ExternalEtfProductMaster> items;

        private ParsedPage(int totalCount, List<ExternalEtfProductMaster> items) {
            this.totalCount = totalCount;
            this.items = items;
        }

        int getTotalCount() {
            return totalCount;
        }

        List<ExternalEtfProductMaster> getItems() {
            return items;
        }
    }
}
