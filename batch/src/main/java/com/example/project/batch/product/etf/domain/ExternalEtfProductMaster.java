package com.example.project.batch.product.etf.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExternalEtfProductMaster {
    private String stockCode;
    private String productName;
    private String issuerName;
}
