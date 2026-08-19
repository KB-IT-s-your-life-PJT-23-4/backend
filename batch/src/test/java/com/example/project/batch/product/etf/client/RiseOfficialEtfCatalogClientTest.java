package com.example.project.batch.product.etf.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiseOfficialEtfCatalogClientTest {

    @Test
    void parsesOfficialProductNameAndStockCodeAfterPublisherVerification() {
        String html = """
                <html>
                  <head><meta name="keywords" content="RISE ETF, KB자산운용"></head>
                  <body>
                    <p>전체 <span>3</span> 건</p>
                    <table><tbody>
                      <tr data-class="dataList">
                        <th><p><a href="/prod/finderDetail/4435">RISE 200</a></p>
                        <span class="code">(148020)</span></th>
                      </tr>
                      <tr data-class="dataList">
                        <th><p><a href="/prod/finderDetail/44B3">RISE 미국S&amp;P500</a></p>
                        <span class="code">(379780)</span></th>
                      </tr>
                      <tr data-class="dataList">
                        <th><p><a href="/prod/finderDetail/44K9">RISE 미국우주위성통신 (0227K0)</a></p></th>
                      </tr>
                    </tbody></table>
                  </body>
                </html>
                """;
        RiseOfficialEtfCatalogClient client = new RiseOfficialEtfCatalogClient(
                null, "https://www.riseetf.co.kr/prod/finder", "test-agent");

        RiseOfficialEtfCatalogClient.ParsedCatalog catalog = client.parsePage(html);

        assertEquals(3, catalog.getTotalCount());
        assertEquals(3, catalog.getItems().size());
        assertEquals("379780", catalog.getItems().get(1).getStockCode());
        assertEquals("RISE 미국S&P500", catalog.getItems().get(1).getProductName());
        assertEquals("KB자산운용", catalog.getItems().get(1).getIssuerName());
        assertEquals("0227K0", catalog.getItems().get(2).getStockCode());
        assertEquals("RISE 미국우주위성통신", catalog.getItems().get(2).getProductName());
    }

    @Test
    void rejectsHtmlThatCannotProveOfficialPublisher() {
        RiseOfficialEtfCatalogClient client = new RiseOfficialEtfCatalogClient(
                null, "https://www.riseetf.co.kr/prod/finder", "test-agent");

        assertThrows(IllegalStateException.class, () -> client.parsePage(
                "<html><p>전체 <span>0</span> 건</p><p>RISE ETF</p></html>"));
    }

    @Test
    void rejectsNonOfficialCatalogHost() {
        assertThrows(IllegalArgumentException.class, () -> new RiseOfficialEtfCatalogClient(
                null, "https://example.com/prod/finder", "test-agent"));
    }
}
