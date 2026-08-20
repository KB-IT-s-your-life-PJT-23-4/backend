package com.example.project.batch.product.etf.client;

import com.example.project.batch.product.etf.domain.ExternalEtfPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FscEtfPriceClient {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RetryingHttpRequester requester;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final int pageSize;

    public FscEtfPriceClient(
            RetryingHttpRequester requester,
            @Value("${fsc.etf.base-url}") String baseUrl,
            @Value("${fsc.etf.api-key}") String apiKey,
            @Value("${etf.api.page-size:1000}") int pageSize
    ) {
        this.requester = requester;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.pageSize = pageSize;
    }

    public List<ExternalEtfPrice> fetchPrices(String stockCode, LocalDate from, LocalDate to) {
        List<ExternalEtfPrice> prices = new ArrayList<>();
        int page = 1;
        int totalCount;

        do {
            URI uri = buildUri(stockCode, from, to, page);
            String body = requester.get(uri, Map.of(), "금융위원회 ETF 시세");
            ParsedPage parsedPage = parsePage(body, stockCode);
            prices.addAll(parsedPage.getItems());
            totalCount = parsedPage.getTotalCount();
            page++;
        } while ((long) (page - 1) * pageSize < totalCount);

        return prices;
    }

    ParsedPage parsePage(String body, String requestedStockCode) {
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
            List<ExternalEtfPrice> prices = new ArrayList<>();
            if (itemNode.isArray()) {
                itemNode.forEach(item -> addItem(prices, item, requestedStockCode));
            } else if (itemNode.isObject()) {
                addItem(prices, itemNode, requestedStockCode);
            }
            return new ParsedPage(totalCount, prices);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("금융위원회 ETF 시세 응답을 해석할 수 없습니다.", exception);
        }
    }

    private void addItem(List<ExternalEtfPrice> prices, JsonNode item, String requestedStockCode) {
        String stockCode = item.path("srtnCd").asText().trim();
        if (!requestedStockCode.equals(stockCode)) {
            return;
        }
        String baseDate = item.path("basDt").asText();
        String closePrice = item.path("clpr").asText();
        if (baseDate.isBlank() || closePrice.isBlank()) {
            return;
        }
        prices.add(new ExternalEtfPrice(
                stockCode,
                LocalDate.parse(baseDate, BASIC_DATE),
                decimal(closePrice),
                "FSC"));
    }

    private URI buildUri(String stockCode, LocalDate from, LocalDate to, int page) {
        String query = "serviceKey=" + apiKey
                + "&resultType=json"
                + "&numOfRows=" + pageSize
                + "&pageNo=" + page
                + "&beginBasDt=" + BASIC_DATE.format(from)
                + "&endBasDt=" + BASIC_DATE.format(to)
                + "&likeSrtnCd=" + encode(stockCode);
        return URI.create(baseUrl + "?" + query);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value.replace(",", "").trim());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static final class ParsedPage {
        private final int totalCount;
        private final List<ExternalEtfPrice> items;

        private ParsedPage(int totalCount, List<ExternalEtfPrice> items) {
            this.totalCount = totalCount;
            this.items = items;
        }

        int getTotalCount() {
            return totalCount;
        }

        List<ExternalEtfPrice> getItems() {
            return items;
        }
    }
}
