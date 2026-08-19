package com.example.project.batch.product.etf.client;

import com.example.project.batch.product.etf.domain.ExternalEtfProductMaster;
import com.example.project.common.product.RiseEtfPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RiseOfficialEtfCatalogClient {

    private static final String VERIFIED_ISSUER = "KB자산운용";
    private static final Pattern TOTAL_COUNT = Pattern.compile(
            "전체\\s*<span>(\\d+)</span>\\s*건", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRODUCT_ROW = Pattern.compile(
            "<tr\\s+data-class=[\"']dataList[\"'][^>]*>(.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PRODUCT_NAME = Pattern.compile(
            "<p>\\s*<a[^>]*>(.*?)</a>\\s*</p>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STOCK_CODE = Pattern.compile(
            "<span\\s+class=[\"']code[\"']>\\s*\\(([^)]+)\\)\\s*</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INLINE_STOCK_CODE = Pattern.compile(
            "\\s*\\(([0-9A-Z]{6})\\)\\s*$", Pattern.CASE_INSENSITIVE);

    private final RetryingHttpRequester requester;
    private final String catalogUrl;
    private final String userAgent;

    public RiseOfficialEtfCatalogClient(
            RetryingHttpRequester requester,
            @Value("${rise.etf.catalog-url}") String catalogUrl,
            @Value("${rise.etf.user-agent:MiriZoom-ETF-Batch/1.0}") String userAgent
    ) {
        this.requester = requester;
        this.catalogUrl = catalogUrl;
        this.userAgent = userAgent;
        verifyTrustedHost(catalogUrl);
    }

    public Map<String, ExternalEtfProductMaster> fetchVerifiedCatalog() {
        ParsedCatalog firstPage = fetchPage(1);
        if (firstPage.getTotalCount() <= 0 || firstPage.getItems().isEmpty()) {
            throw new IllegalStateException("RISE 공식 상품 목록이 비어 있습니다.");
        }

        int pageSize = firstPage.getItems().size();
        int lastPage = (int) Math.ceil((double) firstPage.getTotalCount() / pageSize);
        Map<String, ExternalEtfProductMaster> verifiedByStockCode = new LinkedHashMap<>();
        addVerifiedItems(verifiedByStockCode, firstPage.getItems());

        if (lastPage > 1) {
            ParsedCatalog lastPageResult = fetchPage(lastPage);
            addVerifiedItems(verifiedByStockCode, lastPageResult.getItems());

            // 현재 공식 페이지는 마지막 페이지 요청에 앞 페이지 결과까지 누적해서 반환합니다.
            // 페이지 방식이 바뀌어도 동작하도록 부족한 경우에만 중간 페이지를 보충합니다.
            if (verifiedByStockCode.size() < firstPage.getTotalCount()) {
                for (int page = 2; page < lastPage; page++) {
                    addVerifiedItems(verifiedByStockCode, fetchPage(page).getItems());
                }
            }
        }

        if (verifiedByStockCode.size() != firstPage.getTotalCount()) {
            throw new IllegalStateException(
                    "RISE 공식 상품 목록 수가 일치하지 않습니다. expected="
                            + firstPage.getTotalCount() + ", actual=" + verifiedByStockCode.size());
        }
        return Map.copyOf(verifiedByStockCode);
    }

    ParsedCatalog parsePage(String html) {
        verifyOfficialPublisher(html);

        Matcher totalMatcher = TOTAL_COUNT.matcher(html);
        if (!totalMatcher.find()) {
            throw new IllegalStateException("RISE 공식 상품 목록의 전체 건수를 찾을 수 없습니다.");
        }

        List<ExternalEtfProductMaster> items = new ArrayList<>();
        Matcher rowMatcher = PRODUCT_ROW.matcher(html);
        while (rowMatcher.find()) {
            String row = rowMatcher.group(1);
            Matcher nameMatcher = PRODUCT_NAME.matcher(row);
            Matcher codeMatcher = STOCK_CODE.matcher(row);
            if (!nameMatcher.find()) {
                continue;
            }
            String productName = decodeHtml(stripTags(nameMatcher.group(1))).trim();
            String stockCode;
            if (codeMatcher.find()) {
                stockCode = decodeHtml(codeMatcher.group(1)).trim();
            } else {
                Matcher inlineCodeMatcher = INLINE_STOCK_CODE.matcher(productName);
                if (!inlineCodeMatcher.find()) {
                    continue;
                }
                stockCode = inlineCodeMatcher.group(1).trim();
                productName = productName.substring(0, inlineCodeMatcher.start()).trim();
            }
            if (RiseEtfPolicy.isRiseProductName(productName) && !stockCode.isBlank()) {
                items.add(new ExternalEtfProductMaster(
                        stockCode, productName, VERIFIED_ISSUER));
            }
        }
        return new ParsedCatalog(Integer.parseInt(totalMatcher.group(1)), items);
    }

    private ParsedCatalog fetchPage(int page) {
        String separator = catalogUrl.contains("?") ? "&" : "?";
        URI uri = URI.create(catalogUrl + separator + "page=" + page + "&searchFieldType=list");
        String html = requester.get(
                uri,
                Map.of(
                        "User-Agent", userAgent,
                        "Accept", "text/html,application/xhtml+xml"),
                "RISE 공식 ETF 상품 목록");
        return parsePage(html);
    }

    private void addVerifiedItems(
            Map<String, ExternalEtfProductMaster> target,
            List<ExternalEtfProductMaster> items
    ) {
        for (ExternalEtfProductMaster item : items) {
            if (!RiseEtfPolicy.isVerifiedRiseEtf(item.getProductName(), item.getIssuerName())) {
                throw new IllegalStateException(
                        "RISE 공식 목록에서 검증할 수 없는 상품을 발견했습니다. stockCode="
                                + item.getStockCode());
            }
            ExternalEtfProductMaster previous = target.putIfAbsent(item.getStockCode(), item);
            if (previous != null && !sameProductName(previous.getProductName(), item.getProductName())) {
                throw new IllegalStateException(
                        "RISE 공식 목록에 같은 종목코드의 다른 상품명이 있습니다. stockCode="
                                + item.getStockCode());
            }
        }
    }

    private void verifyOfficialPublisher(String html) {
        if (html == null || !html.contains("KB자산운용") || !html.contains("RISE ETF")) {
            throw new IllegalStateException("RISE 공식 상품 페이지의 발행 주체를 확인할 수 없습니다.");
        }
    }

    private void verifyTrustedHost(String url) {
        String host = URI.create(url).getHost();
        if (host == null || !("riseetf.co.kr".equalsIgnoreCase(host)
                || host.toLowerCase().endsWith(".riseetf.co.kr"))) {
            throw new IllegalArgumentException("RISE 공식 도메인만 상품 검증에 사용할 수 있습니다.");
        }
    }

    private boolean sameProductName(String left, String right) {
        return normalizeProductName(left).equals(normalizeProductName(right));
    }

    private String normalizeProductName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase();
    }

    private String stripTags(String value) {
        return value.replaceAll("<[^>]+>", "");
    }

    private String decodeHtml(String value) {
        return value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    static final class ParsedCatalog {
        private final int totalCount;
        private final List<ExternalEtfProductMaster> items;

        private ParsedCatalog(int totalCount, List<ExternalEtfProductMaster> items) {
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
