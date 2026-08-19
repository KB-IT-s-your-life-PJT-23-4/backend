package com.example.project.common.product;

import java.util.Locale;

/**
 * RISE ETF 여부를 판별하는 공통 정책입니다.
 *
 * 별도 허용 목록이나 운용사 컬럼을 두지 않으므로, 상품 데이터에 저장된
 * 공식 브랜드명을 기준으로 수집·추천·관리 대상을 제한합니다.
 */
public final class RiseEtfPolicy {

    private static final String BRAND = "RISE";

    private RiseEtfPolicy() {
    }

    public static boolean isRiseProductName(String productName) {
        if (productName == null) {
            return false;
        }

        String normalized = productName.strip();
        if (!normalized.regionMatches(true, 0, BRAND, 0, BRAND.length())) {
            return false;
        }

        return normalized.length() == BRAND.length()
                || Character.isWhitespace(normalized.charAt(BRAND.length()));
    }

    /**
     * 외부 상품 마스터 응답이 RISE 브랜드와 KB자산운용을 모두 가리키는지 확인합니다.
     * 상품명만으로는 동명·오표기 데이터를 걸러낼 수 없으므로 운용사 확인을 필수로 둡니다.
     */
    public static boolean isVerifiedRiseEtf(String productName, String issuerName) {
        return isRiseProductName(productName) && isKbAssetManagementIssuer(issuerName);
    }

    public static boolean isKbAssetManagementIssuer(String issuerName) {
        if (issuerName == null) {
            return false;
        }

        String normalized = issuerName.toUpperCase(Locale.ROOT)
                .replaceAll("[^0-9A-Z가-힣]", "");
        return normalized.contains("KB자산운용")
                || normalized.contains("케이비자산운용")
                || normalized.contains("KBASSETMANAGEMENT");
    }
}
