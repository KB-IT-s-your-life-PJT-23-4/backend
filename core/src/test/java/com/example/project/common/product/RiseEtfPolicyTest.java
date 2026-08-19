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

    @Test
    @DisplayName("RISE 상품명과 KB자산운용 운용사가 모두 일치해야 한다")
    void verifiesBrandAndIssuerTogether() {
        assertTrue(RiseEtfPolicy.isVerifiedRiseEtf("RISE 코리아200", "KB자산운용"));
        assertTrue(RiseEtfPolicy.isVerifiedRiseEtf(
                "RISE 미국S&P500", "KB Asset Management Co., Ltd."));
        assertTrue(RiseEtfPolicy.isVerifiedRiseEtf("RISE 채권혼합", "(주)케이비자산운용"));

        assertFalse(RiseEtfPolicy.isVerifiedRiseEtf("RISE 코리아200", "다른자산운용"));
        assertFalse(RiseEtfPolicy.isVerifiedRiseEtf("다른 ETF", "KB자산운용"));
        assertFalse(RiseEtfPolicy.isVerifiedRiseEtf("RISE 코리아200", null));
    }
}
