package com.example.project.admin.lawtax.mapper;

import com.example.project.admin.lawtax.domain.GiftDeductionLimitRow;
import com.example.project.admin.lawtax.domain.GiftTaxBracketRow;
import com.example.project.admin.lawtax.domain.LawArticleRow;
import com.example.project.admin.lawtax.domain.LawSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminLawTaxMapper {

    List<GiftTaxBracketRow> selectAllBrackets();

    List<GiftDeductionLimitRow> selectAllDeductionLimits();

    List<GiftTaxBracketRow> selectBracketsByEffectiveFrom(@Param("effectiveFrom") LocalDate effectiveFrom);

    List<GiftDeductionLimitRow> selectDeductionLimitsByEffectiveFrom(@Param("effectiveFrom") LocalDate effectiveFrom);

    LocalDate selectMaxBracketEffectiveFrom();

    int setBracketEffectiveTo(@Param("effectiveFrom") LocalDate effectiveFrom, @Param("effectiveTo") LocalDate effectiveTo);

    int setDeductionLimitEffectiveTo(@Param("effectiveFrom") LocalDate effectiveFrom, @Param("effectiveTo") LocalDate effectiveTo);

    int insertBracket(
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("lowerBound") Long lowerBound,
            @Param("upperBound") Long upperBound,
            @Param("taxRate") BigDecimal taxRate,
            @Param("progressiveDeduction") Long progressiveDeduction
    );

    int insertDeductionLimit(
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("relation") String relation,
            @Param("isMinor") boolean isMinor,
            @Param("deductionLimit") Long deductionLimit
    );

    int deleteBracketsByEffectiveFrom(@Param("effectiveFrom") LocalDate effectiveFrom);

    int deleteDeductionLimitsByEffectiveFrom(@Param("effectiveFrom") LocalDate effectiveFrom);

    List<LawArticleRow> selectLawArticlePage(
            @Param("lawCode") String lawCode,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size
    );

    long countLawArticles(@Param("lawCode") String lawCode, @Param("keyword") String keyword);

    LawArticleRow selectLawArticleById(@Param("lawId") Long lawId);

    List<LawSummaryRow> selectDistinctLaws();
}
