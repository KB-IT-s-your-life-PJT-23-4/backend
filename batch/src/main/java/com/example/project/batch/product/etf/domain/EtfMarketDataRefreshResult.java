package com.example.project.batch.product.etf.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EtfMarketDataRefreshResult {
    private int registeredMasterCount;
    private int targetCount;
    private int storedPriceCount;
    private Long publishedDataVersionId;
}
