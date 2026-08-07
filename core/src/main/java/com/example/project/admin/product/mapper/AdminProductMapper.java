package com.example.project.admin.product.mapper;

import com.example.project.admin.product.domain.AdminProductRow;
import com.example.project.admin.product.domain.AdminProductVersionRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminProductMapper {

    List<AdminProductVersionRow> selectProductDataVersions();

    AdminProductVersionRow selectProductDataVersion(
            @Param("productDataVersionId") Long productDataVersionId);

    AdminProductVersionRow selectProductDataVersionByCode(
            @Param("versionCode") String versionCode);

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

    int updateProductVersion(
            @Param("productVersionId") Long productVersionId,
            @Param("productName") String productName,
            @Param("description") String description,
            @Param("productUrl") String productUrl,
            @Param("salesStatus") String salesStatus);

    int insertProductDataVersion(
            @Param("versionCode") String versionCode,
            @Param("dataDate") LocalDate dataDate);
}