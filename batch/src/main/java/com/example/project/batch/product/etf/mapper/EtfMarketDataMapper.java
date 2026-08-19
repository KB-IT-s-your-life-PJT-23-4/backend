package com.example.project.batch.product.etf.mapper;

import com.example.project.batch.product.etf.domain.EtfHistoryPrice;
import com.example.project.batch.product.etf.domain.EtfPricePoint;
import com.example.project.batch.product.etf.domain.EtfProductTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface EtfMarketDataMapper {

    List<EtfProductTarget> selectCurrentEtfTargets();

    Long selectProductIdByEtfStockCode(@Param("stockCode") String stockCode);

    String selectProductTypeByCode(@Param("productCode") String productCode);

    int insertEtfProductMaster(@Param("productCode") String productCode);

    int upsertHistoryPrices(@Param("prices") List<EtfHistoryPrice> prices);

    EtfPricePoint selectFirstPriceInWindow(
            @Param("productId") Long productId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    EtfPricePoint selectLatestPriceOnOrBefore(
            @Param("productId") Long productId,
            @Param("to") LocalDate to);

    Long selectLatestCompletedDataVersionId();

    int countLoadingDataVersions();

    int insertProductDataVersion(
            @Param("versionCode") String versionCode,
            @Param("dataDate") LocalDate dataDate);

    Long selectLastInsertedId();

    int cloneProductVersions(
            @Param("sourceDataVersionId") Long sourceDataVersionId,
            @Param("targetDataVersionId") Long targetDataVersionId);

    int cloneDeposits(
            @Param("sourceDataVersionId") Long sourceDataVersionId,
            @Param("targetDataVersionId") Long targetDataVersionId);

    int cloneSavings(
            @Param("sourceDataVersionId") Long sourceDataVersionId,
            @Param("targetDataVersionId") Long targetDataVersionId);

    int cloneEtfs(
            @Param("sourceDataVersionId") Long sourceDataVersionId,
            @Param("targetDataVersionId") Long targetDataVersionId);

    int cloneBaseInterestRates(
            @Param("sourceDataVersionId") Long sourceDataVersionId,
            @Param("targetDataVersionId") Long targetDataVersionId);

    int clonePreferentialInterestRates(
            @Param("sourceDataVersionId") Long sourceDataVersionId,
            @Param("targetDataVersionId") Long targetDataVersionId);

    int cloneEtfHoldings(
            @Param("sourceDataVersionId") Long sourceDataVersionId,
            @Param("targetDataVersionId") Long targetDataVersionId);

    int updateEtfAnnualReturn(
            @Param("targetDataVersionId") Long targetDataVersionId,
            @Param("productId") Long productId,
            @Param("annualReturn10yPercent") BigDecimal annualReturn10yPercent);

    int completeProductDataVersion(@Param("targetDataVersionId") Long targetDataVersionId);
}
