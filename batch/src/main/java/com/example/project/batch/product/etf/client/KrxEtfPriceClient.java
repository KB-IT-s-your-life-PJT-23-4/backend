package com.example.project.batch.product.etf.client;

import com.example.project.batch.product.etf.domain.ExternalEtfPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class KrxEtfPriceClient {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RetryingHttpRequester requester;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String authKey;

    public KrxEtfPriceClient(
            RetryingHttpRequester requester,
            @Value("${krx.etf.base-url}") String baseUrl,
            @Value("${krx.etf.auth-key}") String authKey
    ) {
        this.requester = requester;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.authKey = authKey;
    }

    public List<ExternalEtfPrice> fetchPrices(LocalDate baseDate) {
        URI uri = URI.create(baseUrl + "?basDd=" + BASIC_DATE.format(baseDate));
        String body = requester.get(uri, Map.of("AUTH_KEY", authKey), "KRX ETF 일별매매정보");
        return parseResponse(body);
    }

    List<ExternalEtfPrice> parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("OutBlock_1");
            if (!items.isArray()) {
                String message = firstText(root, "message", "msg1", "RESULT_MSG");
                if (!message.isBlank()) {
                    throw new IllegalStateException("KRX ETF 일별매매정보 API 오류: " + message);
                }
                return List.of();
            }

            List<ExternalEtfPrice> prices = new ArrayList<>();
            for (JsonNode item : items) {
                String stockCode = firstText(item, "ISU_SRT_CD", "ISU_CD", "SRTPRC_CD").trim();
                String date = firstText(item, "BAS_DD", "BAS_DT");
                String closePrice = firstText(item, "TDD_CLSPRC", "CLSPRC", "CLPR");
                if (stockCode.isBlank() || date.isBlank() || closePrice.isBlank()) {
                    continue;
                }
                prices.add(new ExternalEtfPrice(
                        stockCode,
                        LocalDate.parse(date, BASIC_DATE),
                        new BigDecimal(closePrice.replace(",", "").trim()),
                        "KRX"));
            }
            return prices;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("KRX ETF 일별매매정보 응답을 해석할 수 없습니다.", exception);
        }
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return "";
    }
}
