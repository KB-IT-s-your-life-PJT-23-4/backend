package com.example.project.batch.product.etf.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FscEtfPriceClientTest {

    @Test
    void parsesOnlyTheRequestedEtfFromFscResponse() {
        String response = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "totalCount": 2,
                      "items": {
                        "item": [
                          {"basDt": "20260818", "srtnCd": "123456", "clpr": "12,345.50"},
                          {"basDt": "20260818", "srtnCd": "654321", "clpr": "9,000"}
                        ]
                      }
                    }
                  }
                }
                """;
        FscEtfPriceClient client = new FscEtfPriceClient(null, "https://example.test", "key", 1000);

        FscEtfPriceClient.ParsedPage page = client.parsePage(response, "123456");

        assertEquals(2, page.getTotalCount());
        assertEquals(1, page.getItems().size());
        assertEquals("123456", page.getItems().get(0).getStockCode());
        assertEquals("12345.50", page.getItems().get(0).getClosePrice().toPlainString());
        assertEquals("FSC", page.getItems().get(0).getDataSource());
    }
}
