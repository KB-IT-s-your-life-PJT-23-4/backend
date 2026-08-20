package com.example.project.batch.product.etf.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FscEtfProductMasterClientTest {

    @Test
    void parsesRiseCandidatesFromEtfPriceResponseWithoutInventingIssuer() {
        String response = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "totalCount": 3,
                      "items": {
                        "item": [
                          {"basDt": "20260818", "srtnCd": "148020", "itmsNm": "RISE 200"},
                          {"basDt": "20260818", "srtnCd": "379780", "itmsNm": "RISE 미국S&P500"},
                          {"basDt": "20260818", "srtnCd": "069500", "itmsNm": "다른 ETF"}
                        ]
                      }
                    }
                  }
                }
                """;
        FscEtfProductMasterClient client = new FscEtfProductMasterClient(
                null, "https://example.test", "key", 1000, 14);

        FscEtfProductMasterClient.ParsedPage page = client.parsePage(response);

        assertEquals(3, page.getTotalCount());
        assertEquals(2, page.getItems().size());
        assertEquals("148020", page.getItems().get(0).getStockCode());
        assertEquals("RISE 200", page.getItems().get(0).getProductName());
        assertNull(page.getItems().get(0).getIssuerName());
    }
}
