package com.example.project.batch.product.etf.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FscEtfProductMasterClientTest {

    @Test
    void parsesStockCodeProductNameAndIssuerFromFundMasterResponse() {
        String response = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "totalCount": 2,
                      "items": {
                        "item": [
                          {
                            "srtnCd": "069500",
                            "fndNm": "RISE 코리아200",
                            "mngCoNm": "KB자산운용"
                          },
                          {
                            "srtnCd": "999999",
                            "fndNm": "RISE 이름만 같은 상품",
                            "mngCoNm": "다른자산운용"
                          }
                        ]
                      }
                    }
                  }
                }
                """;
        FscEtfProductMasterClient client = new FscEtfProductMasterClient(
                null, "https://example.test", "key", 1000);

        FscEtfProductMasterClient.ParsedPage page = client.parsePage(response);

        assertEquals(2, page.getTotalCount());
        assertEquals(2, page.getItems().size());
        assertEquals("069500", page.getItems().get(0).getStockCode());
        assertEquals("RISE 코리아200", page.getItems().get(0).getProductName());
        assertEquals("KB자산운용", page.getItems().get(0).getIssuerName());
    }

    @Test
    void recognizesKbAssetManagementEvenWhenIssuerFieldNameChanges() {
        String response = """
                {
                  "response": {
                    "header": {"resultCode": "00"},
                    "body": {
                      "totalCount": 1,
                      "items": {
                        "item": {
                          "srtnCd": "379780",
                          "fndNm": "RISE 미국S&P500",
                          "managerDisplayValue": "KB Asset Management Co., Ltd."
                        }
                      }
                    }
                  }
                }
                """;
        FscEtfProductMasterClient client = new FscEtfProductMasterClient(
                null, "https://example.test", "key", 1000);

        FscEtfProductMasterClient.ParsedPage page = client.parsePage(response);

        assertEquals("KB Asset Management Co., Ltd.", page.getItems().get(0).getIssuerName());
    }
}
