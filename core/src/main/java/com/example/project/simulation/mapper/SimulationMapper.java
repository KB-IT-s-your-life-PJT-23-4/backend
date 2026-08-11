package com.example.project.simulation.mapper;

import com.example.project.simulation.domain.BaseRateRecord;
import com.example.project.simulation.domain.DeductionRule;
import com.example.project.simulation.domain.EtfHoldingRecord;
import com.example.project.simulation.domain.EtfPriceRecord;
import com.example.project.simulation.domain.FamilySnapshot;
import com.example.project.simulation.domain.GiftHistoryRecord;
import com.example.project.simulation.domain.PreferentialRateRecord;
import com.example.project.simulation.domain.ProductCandidate;
import com.example.project.simulation.domain.ProductDataVersionRecord;
import com.example.project.simulation.domain.ProductVersionDetailRecord;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxBracket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SimulationMapper {

    FamilySnapshot selectFamily(@Param("familyId") Long familyId);

    FamilySnapshot lockFamily(@Param("familyId") Long familyId);

    DeductionRule selectDeductionRule(
            @Param("relation") String relation,
            @Param("minor") boolean minor,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<GiftHistoryRecord> selectCompletedGifts(
            @Param("familyId") Long familyId,
            @Param("windowStart") LocalDate windowStart,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<TaxBracket> selectTaxBrackets(@Param("asOfDate") LocalDate asOfDate);

    ProductDataVersionRecord selectLatestCompletedProductDataVersion();

    ProductDataVersionRecord selectProductDataVersion(@Param("productDataVersionId") Long id);

    List<ProductCandidate> selectDepositCandidates(
            @Param("productDataVersionId") Long productDataVersionId,
            @Param("investmentPeriodMonths") Integer investmentPeriodMonths,
            @Param("limit") Integer limit
    );

    List<ProductCandidate> selectSavingsCandidates(
            @Param("productDataVersionId") Long productDataVersionId,
            @Param("investmentPeriodMonths") Integer investmentPeriodMonths,
            @Param("limit") Integer limit
    );

    List<ProductCandidate> selectEtfCandidates(
            @Param("productDataVersionId") Long productDataVersionId,
            @Param("riskProfile") RiskProfile riskProfile,
            @Param("limit") Integer limit
    );

    int insertSimulation(SimulationRecord simulation);

    int insertResult(SimulationResultRecord result);

    int insertTranche(SimulationTrancheRecord tranche);

    int insertPortfolio(SimulationPortfolioRecord portfolio);

    int updatePortfolioExpectedFutureValue(
            @Param("portfolioId") Long portfolioId,
            @Param("expectedFutureValue") Long expectedFutureValue
    );

    int markPortfolioRecommended(@Param("portfolioId") Long portfolioId);

    int insertProductSnapshot(SimulationProductRecord product);

    SimulationRecord selectSimulation(@Param("simulationId") Long simulationId);

    SimulationRecord selectSavedSimulationByFamily(@Param("familyId") Long familyId);

    List<SimulationResultRecord> selectResults(@Param("simulationId") Long simulationId);

    List<SimulationTrancheRecord> selectTranches(@Param("simulationId") Long simulationId);

    List<SimulationPortfolioRecord> selectPortfolios(@Param("simulationId") Long simulationId);

    SimulationPortfolioRecord selectPortfolio(@Param("portfolioId") Long portfolioId);

    List<SimulationProductRecord> selectProductSnapshots(@Param("simulationId") Long simulationId);

    List<SimulationProductRecord> selectPortfolioProducts(@Param("portfolioId") Long portfolioId);

    SimulationProductRecord selectSimulationProduct(
            @Param("simulationProductId") Long simulationProductId
    );

    ProductVersionDetailRecord selectProductVersionDetail(
            @Param("productVersionId") Long productVersionId
    );

    boolean existsProductInSimulation(
            @Param("simulationId") Long simulationId,
            @Param("productVersionId") Long productVersionId
    );

    List<BaseRateRecord> selectBaseRates(@Param("productVersionId") Long productVersionId);

    List<PreferentialRateRecord> selectPreferentialRates(
            @Param("productVersionId") Long productVersionId
    );

    List<PreferentialRateRecord> selectPreferentialRatesByCodes(
            @Param("productVersionId") Long productVersionId,
            @Param("conditionCodes") List<String> conditionCodes
    );

    List<PreferentialRateRecord> selectSelectedPreferentialRates(
            @Param("simulationProductId") Long simulationProductId
    );

    List<EtfHoldingRecord> selectEtfHoldings(
            @Param("productVersionId") Long productVersionId,
            @Param("limit") Integer limit
    );

    List<EtfPriceRecord> selectRecentEtfPrices(
            @Param("productId") Long productId,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("limit") Integer limit
    );

    List<SimulationRecord> selectSimulationPage(
            @Param("userId") Long userId,
            @Param("status") SimulationStatus status,
            @Param("familyId") Long familyId,
            @Param("now") LocalDateTime now,
            @Param("offset") long offset,
            @Param("size") int size
    );

    List<SimulationResultRecord> selectResultsBySimulationIds(
            @Param("simulationIds") List<Long> simulationIds
    );

    List<SimulationPortfolioRecord> selectRecommendedPortfoliosBySimulationIds(
            @Param("simulationIds") List<Long> simulationIds
    );

    List<SimulationPortfolioRecord> selectPortfoliosByIds(
            @Param("portfolioIds") List<Long> portfolioIds
    );

    List<SimulationProductRecord> selectSelectedProductsByPortfolioIds(
            @Param("portfolioIds") List<Long> portfolioIds
    );

    long countSimulations(
            @Param("userId") Long userId,
            @Param("status") SimulationStatus status,
            @Param("familyId") Long familyId,
            @Param("now") LocalDateTime now
    );

    int clearSimulationSelections(@Param("simulationId") Long simulationId);

    int deleteSimulationPreferentialConditions(@Param("simulationId") Long simulationId);

    int restoreSimulationProduct(
            @Param("simulationProductId") Long simulationProductId,
            @Param("appliedAnnualRate") BigDecimal appliedAnnualRate,
            @Param("expectedFutureValue") Long expectedFutureValue
    );

    int markSimulationProductSelected(
            @Param("simulationProductId") Long simulationProductId,
            @Param("appliedAnnualRate") BigDecimal appliedAnnualRate,
            @Param("expectedFutureValue") Long expectedFutureValue
    );

    int insertSelectedPreferentialCondition(
            @Param("simulationProductId") Long simulationProductId,
            @Param("preferentialInterestRateId") Long preferentialInterestRateId
    );

    int saveSimulation(
            @Param("simulationId") Long simulationId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("selectedPortfolioId") Long selectedPortfolioId,
            @Param("savedAt") LocalDateTime savedAt
    );

    int resetSavedSimulation(
            @Param("simulationId") Long simulationId,
            @Param("expiredAt") LocalDateTime expiredAt
    );
}
