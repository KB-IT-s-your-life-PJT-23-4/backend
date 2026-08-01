package com.example.project.simulation.mapper;

import com.example.project.simulation.domain.DeductionRule;
import com.example.project.simulation.domain.FamilySnapshot;
import com.example.project.simulation.domain.GiftAggregation;
import com.example.project.simulation.domain.GiftHistoryRecord;
import com.example.project.simulation.domain.ProductCandidate;
import com.example.project.simulation.domain.ProductType;
import com.example.project.simulation.domain.RiskProfile;
import com.example.project.simulation.domain.ScenarioType;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationResultRecord;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.simulation.domain.TaxBracket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SimulationMapper {

    FamilySnapshot selectFamily(@Param("familyId") Long familyId);

    DeductionRule selectDeductionRule(
            @Param("relation") String relation,
            @Param("minor") boolean minor,
            @Param("asOfDate") LocalDate asOfDate
    );

    GiftAggregation selectGiftAggregation(
            @Param("familyId") Long familyId,
            @Param("windowStart") LocalDate windowStart,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<GiftHistoryRecord> selectCompletedGifts(
            @Param("familyId") Long familyId,
            @Param("windowStart") LocalDate windowStart,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<TaxBracket> selectTaxBrackets(@Param("asOfDate") LocalDate asOfDate);

    List<ProductCandidate> selectDepositCandidates(
            @Param("investmentPeriodMonths") Integer investmentPeriodMonths,
            @Param("limit") Integer limit
    );

    List<ProductCandidate> selectSavingsCandidates(
            @Param("investmentPeriodMonths") Integer investmentPeriodMonths,
            @Param("limit") Integer limit
    );

    List<ProductCandidate> selectEtfCandidates(@Param("limit") Integer limit);

    int insertSimulation(SimulationRecord simulation);

    int insertResult(SimulationResultRecord result);

    int updateResultFutureValue(
            @Param("resultId") Long resultId,
            @Param("expectedFutureValue") Long expectedFutureValue,
            @Param("riskProfile") RiskProfile riskProfile
    );

    int insertTranche(SimulationTrancheRecord tranche);

    int insertProductSnapshot(SimulationProductRecord product);

    SimulationRecord selectSimulation(@Param("simulationId") Long simulationId);

    List<SimulationResultRecord> selectResults(@Param("simulationId") Long simulationId);

    List<SimulationTrancheRecord> selectTranches(@Param("simulationId") Long simulationId);

    List<SimulationProductRecord> selectProductSnapshots(@Param("simulationId") Long simulationId);

    int clearSelectedProducts(@Param("resultId") Long resultId);

    int selectProduct(
            @Param("simulationProductId") Long simulationProductId,
            @Param("allocatedAmount") Long allocatedAmount,
            @Param("allocationRatio") java.math.BigDecimal allocationRatio,
            @Param("appliedAnnualRatePercent") java.math.BigDecimal appliedAnnualRatePercent,
            @Param("expectedFutureValue") Long expectedFutureValue,
            @Param("expectedProfit") Long expectedProfit
    );

    int saveSimulation(
            @Param("simulationId") Long simulationId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("selectedScenarioType") ScenarioType selectedScenarioType,
            @Param("selectedResultId") Long selectedResultId,
            @Param("selectedRiskProfile") RiskProfile selectedRiskProfile,
            @Param("savedAt") LocalDateTime savedAt,
            @Param("expiredAt") LocalDateTime expiredAt
    );
}
