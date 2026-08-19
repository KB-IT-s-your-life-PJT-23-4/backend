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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class FscEtfProductMasterClient {

    private static final String[] ISSUER_FIELD_NAMES = {
            "corpNm", "issuerName", "mngCoNm", "mngCmpyNm", "assetMngCoNm"
    };

    private final RetryingHttpRequester requester;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final int pageSize;

    public FscEtfProductMasterClient(
            RetryingHttpRequester requester,
            @Value("${fsc.etf-master.base-url}") String baseUrl,
            @Value("${fsc.etf-master.api-key:${fsc.etf.api-key}}") String apiKey,
            @Value("${etf.api.page-size:1000}") int pageSize
    ) {
        this.requester = requester;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.pageSize = pageSize;
    }

    public List<ExternalEtfProductMaster> fetchRiseCandidates() {
        List<ExternalEtfProductMaster> products = new ArrayList<>();
        int page = 1;
        int totalCount;
        do {
            String body = requester.get(buildUri(page), Map.of(), "금융위원회 KRX상장종목정보");
            ParsedPage parsedPage = parsePage(body);
            products.addAll(parsedPage.getItems());
            totalCount = parsedPage.getTotalCount();
            page++;
        } while ((long) (page - 1) * pageSize < totalCount);
        return products;
    }

    ParsedPage parsePage(String body) {
        try {
            JsonNode response = objectMapper.readTree(body).path("response");
            JsonNode header = response.path("header");
            String resultCode = header.path("resultCode").asText();
            if (!resultCode.isBlank() && !"00".equals(resultCode)) {
                throw new IllegalStateException("금융위원회 KRX상장종목정보 API 오류: "
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
            throw new IllegalStateException("금융위원회 KRX상장종목정보 응답을 해석할 수 없습니다.", exception);
        }
    }

    private void addItem(List<ExternalEtfProductMaster> items, JsonNode item) {
        String stockCode = text(item, "srtnCd");
        String productName = firstText(item, "itmsNm", "productName");
        if (stockCode.isBlank() || productName.isBlank()) {
            return;
        }
        items.add(new ExternalEtfProductMaster(
                stockCode,
                productName,
                findIssuerName(item)));
    }

    private String findIssuerName(JsonNode item) {
        String issuerName = firstText(item, ISSUER_FIELD_NAMES);
        if (!issuerName.isBlank()) {
            return issuerName;
        }

        // 공공데이터 응답 필드명이 바뀌더라도 운용사 값 자체가 명확한 경우만 보수적으로 수용합니다.
        Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isValueNode()
                    && RiseEtfPolicy.isKbAssetManagementIssuer(field.getValue().asText())) {
                return field.getValue().asText().trim();
            }
        }
        return "";
    }

    private URI buildUri(int page) {
        String query = "serviceKey=" + apiKey
                + "&resultType=json"
                + "&numOfRows=" + pageSize
                + "&pageNo=" + page
                + "&likeItmsNm=" + encode("RISE");
        return URI.create(baseUrl + "?" + query);
    }

    private String firstText(JsonNode item, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(item, fieldName);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String text(JsonNode item, String fieldName) {
        return item.path(fieldName).asText("").trim();
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
