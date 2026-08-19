package com.example.project.common.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiseEtfPolicyTest {

    @Test
    @DisplayName("RISE 브랜드 상품명만 ETF 수집 대상으로 인정한다")
    void acceptsOnlyRiseBrandNames() {
        assertTrue(RiseEtfPolicy.isRiseProductName("RISE 코리아200"));
        assertTrue(RiseEtfPolicy.isRiseProductName("  rise 미국S&P500  "));
        assertTrue(RiseEtfPolicy.isRiseProductName("RISE\t채권혼합"));

        assertFalse(RiseEtfPolicy.isRiseProductName("KBSTAR 코리아200"));
        assertFalse(RiseEtfPolicy.isRiseProductName("RISEPLUS ETF"));
        assertFalse(RiseEtfPolicy.isRiseProductName(null));
    }
}
