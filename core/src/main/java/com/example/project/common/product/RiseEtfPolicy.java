package com.example.project.common.product;

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
}
