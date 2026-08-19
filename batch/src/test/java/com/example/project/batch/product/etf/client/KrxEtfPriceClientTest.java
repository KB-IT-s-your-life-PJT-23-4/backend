package com.example.project.batch.product.etf.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KrxEtfPriceClientTest {

    @Test
    void parsesKrxDailyEtfPrices() {
        String response = """
                {
                  "OutBlock_1": [
                    {
                      "BAS_DD": "20260818",
                      "ISU_CD": "KR7123450000",
                      "ISU_SRT_CD": "123450",
                      "TDD_CLSPRC": "10,250"
                    }
                  ]
                }
                """;
        KrxEtfPriceClient client = new KrxEtfPriceClient(null, "https://example.test", "key");

        var prices = client.parseResponse(response);

        assertEquals(1, prices.size());
        assertEquals("123450", prices.get(0).getStockCode());
        assertEquals("10250", prices.get(0).getClosePrice().toPlainString());
        assertEquals("KRX", prices.get(0).getDataSource());
    }
}
