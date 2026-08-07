package com.example.project.admin.product.mapper;

import com.example.project.admin.product.domain.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminProductMapper {

    List<AdminProductVersionRow> selectProductDataVersions();

    AdminProductVersionRow selectProductDataVersion(
            @Param("productDataVersionId") Long productDataVersionId);

    AdminProductVersionRow selectProductDataVersionByCode(
            @Param("versionCode") String versionCode);

    AdminProductVersionRow selectLatestCompletedDataVersion();

    List<String> selectVersionCodesByPrefix(@Param("prefix") String prefix);

    String selectProductType(@Param("productVersionId") Long productVersionId);

    List<AdminProductRow> selectDepositProducts(
            @Param("productDataVersionId") Long productDataVersionId,
            @Param("productVersionId") Long productVersionId);

    List<AdminProductRow> selectSavingsProducts(
            @Param("productDataVersionId") Long productDataVersionId,
            @Param("productVersionId") Long productVersionId);

    List<AdminProductRow> selectEtfProducts(
            @Param("productDataVersionId") Long productDataVersionId,
            @Param("productVersionId") Long productVersionId);

    List<AdminBaseRateRow> selectBaseRateTiers(@Param("productVersionId") Long productVersionId);

    int updateProductVersion(
            @Param("productVersionId") Long productVersionId,
            @Param("productName") String productName,
            @Param("description") String description,
            @Param("productUrl") String productUrl,
            @Param("salesStatus") String salesStatus);

    int updateDepositDetail(
            @Param("productVersionId") Long productVersionId,
            @Param("minAmount") Long minAmount,
            @Param("maxAmount") Long maxAmount,
            @Param("minMonth") Integer minMonth,
            @Param("maxMonth") Integer maxMonth);

    int updateSavingsDetail(
            @Param("productVersionId") Long productVersionId,
            @Param("savingsCategory") String savingsCategory,
            @Param("monthlyMinAmount") Long monthlyMinAmount,
            @Param("monthlyMaxAmount") Long monthlyMaxAmount,
            @Param("minMonth") Integer minMonth,
            @Param("maxMonth") Integer maxMonth);

    int updateEtfDetail(
            @Param("productVersionId") Long productVersionId,
            @Param("stockCode") String stockCode,
            @Param("etfCategory") String etfCategory,
            @Param("trackingIndex") String trackingIndex,
            @Param("bondRatioPercent") BigDecimal bondRatioPercent,
            @Param("riskLevel") String riskLevel);

    int updateBaseInterestRate(
            @Param("baseInterestRateId") Long baseInterestRateId,
            @Param("minMonth") Integer minMonth,
            @Param("maxMonth") Integer maxMonth,
            @Param("baseRatePercent") BigDecimal baseRatePercent,
            @Param("maxRatePercent") BigDecimal maxRatePercent,
            @Param("baseDate") LocalDate baseDate);

    int deleteBaseInterestRate(@Param("baseInterestRateId") Long baseInterestRateId);

    int updateEtfReturn(
            @Param("productVersionId") Long productVersionId,
            @Param("annualReturn5yPercent") BigDecimal annualReturn5yPercent);

    int insertProductDataVersion(
            @Param("versionCode") String versionCode,
            @Param("dataDate") LocalDate dataDate);

    int completeProductDataVersion(@Param("productDataVersionId") Long productDataVersionId);

    int insertProduct(
            @Param("productCode") String productCode,
            @Param("productType") String productType);

    int insertProductVersion(
            @Param("productDataVersionId") Long productDataVersionId,
            @Param("productId") Long productId,
            @Param("productName") String productName,
            @Param("description") String description,
            @Param("productUrl") String productUrl,
            @Param("salesStatus") String salesStatus);

    Long selectLastInsertedId();

    int insertBaseInterestRate(
            @Param("productVersionId") Long productVersionId,
            @Param("minMonth") Integer minMonth,
            @Param("maxMonth") Integer maxMonth,
            @Param("baseRatePercent") BigDecimal baseRatePercent,
            @Param("maxRatePercent") BigDecimal maxRatePercent,
            @Param("baseDate") LocalDate baseDate);

    int insertDeposit(
            @Param("productVersionId") Long productVersionId,
            @Param("minAmount") Long minAmount,
            @Param("maxAmount") Long maxAmount,
            @Param("minMonth") Integer minMonth,
            @Param("maxMonth") Integer maxMonth);

    int insertSavings(
            @Param("productVersionId") Long productVersionId,
            @Param("savingsCategory") String savingsCategory,
            @Param("monthlyMinAmount") Long monthlyMinAmount,
            @Param("monthlyMaxAmount") Long monthlyMaxAmount,
            @Param("minMonth") Integer minMonth,
            @Param("maxMonth") Integer maxMonth);

    int insertEtf(
            @Param("productVersionId") Long productVersionId,
            @Param("stockCode") String stockCode,
            @Param("etfCategory") String etfCategory,
            @Param("trackingIndex") String trackingIndex,
            @Param("annualReturn5yPercent") BigDecimal annualReturn5yPercent,
            @Param("bondRatioPercent") BigDecimal bondRatioPercent,
            @Param("riskLevel") String riskLevel);

    List<AdminPreferentialRateRow> selectPreferentialRates(@Param("productVersionId") Long productVersionId);

    int insertPreferentialRate(
            @Param("productVersionId") Long productVersionId,
            @Param("additionalRatePercent") BigDecimal additionalRatePercent,
            @Param("conditionCode") String conditionCode,
            @Param("preferentialCondition") String preferentialCondition,
            @Param("baseDate") LocalDate baseDate);

    int updatePreferentialRate(
            @Param("preferentialInterestRateId") Long preferentialInterestRateId,
            @Param("additionalRatePercent") BigDecimal additionalRatePercent,
            @Param("conditionCode") String conditionCode,
            @Param("preferentialCondition") String preferentialCondition,
            @Param("baseDate") LocalDate baseDate);

    int deletePreferentialRate(@Param("preferentialInterestRateId") Long preferentialInterestRateId);

    List<AdminEtfHoldingRow> selectEtfHoldings(@Param("productVersionId") Long productVersionId);

    int insertEtfHolding(
            @Param("productVersionId") Long productVersionId,
            @Param("holdingRank") Integer holdingRank,
            @Param("holdingName") String holdingName,
            @Param("holdingCode") String holdingCode,
            @Param("assetType") String assetType,
            @Param("countryCode") String countryCode,
            @Param("weightPercent") BigDecimal weightPercent,
            @Param("baseDate") LocalDate baseDate);

    int updateEtfHolding(
            @Param("holdingId") Long holdingId,
            @Param("holdingRank") Integer holdingRank,
            @Param("holdingName") String holdingName,
            @Param("holdingCode") String holdingCode,
            @Param("assetType") String assetType,
            @Param("countryCode") String countryCode,
            @Param("weightPercent") BigDecimal weightPercent,
            @Param("baseDate") LocalDate baseDate);

    int deleteEtfHolding(@Param("holdingId") Long holdingId);
}