package com.example.project.simulation.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.project.simulation.domain.SimulationPortfolioRecord;
import com.example.project.simulation.domain.SimulationProductRecord;
import com.example.project.simulation.domain.SimulationTrancheRecord;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SimulationMapperXmlTest {

    @Test
    @DisplayName("시뮬레이션 MyBatis 매퍼 XML의 모든 구문을 파싱한다")
    void parseMapperXml() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/simulation/SimulationMapper.xml";

        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder builder = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            );
            builder.parse();
        }

        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.insertSimulation"));
        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.selectProductSnapshots"));
        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.selectSimulationPage"));
        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.selectProductVersionDetail"));
        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.resetSavedSimulation"));
        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.saveSimulation"));
        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.deletePortfolio"));
        assertTrue(configuration.hasStatement(
                "com.example.project.simulation.mapper.SimulationMapper.updateDraftVersion"));

        String namespace = "com.example.project.simulation.mapper.SimulationMapper.";
        String insertSimulation = sql(configuration, namespace + "insertSimulation",
                new com.example.project.simulation.domain.SimulationRecord());
        String selectLatestProductDataVersion = sql(
                configuration,
                namespace + "selectLatestCompletedProductDataVersion",
                null
        );
        String selectProductDataVersion = sql(
                configuration,
                namespace + "selectProductDataVersion",
                1L
        );
        String selectSimulation = sql(
                configuration,
                namespace + "selectSimulation",
                1L
        );
        String selectCompletedGifts = sql(
                configuration,
                namespace + "selectCompletedGifts",
                Map.of(
                        "familyId", 1L,
                        "windowStart", LocalDate.of(2016, 8, 12),
                        "giftDate", LocalDate.of(2026, 8, 12)
                )
        );
        String insertTranche = sql(configuration, namespace + "insertTranche",
                new SimulationTrancheRecord());
        String selectTranches = sql(configuration, namespace + "selectTranches", 1L);
        String insertPortfolio = sql(configuration, namespace + "insertPortfolio",
                new SimulationPortfolioRecord());
        String insertProduct = sql(configuration, namespace + "insertProductSnapshot",
                new SimulationProductRecord());
        String selectBaseRates = sql(
                configuration,
                namespace + "selectBaseRates",
                1L
        );
        String selectPreferentialRates = sql(
                configuration,
                namespace + "selectPreferentialRates",
                1L
        );
        String selectSelectedPreferentialRates = sql(
                configuration,
                namespace + "selectSelectedPreferentialRates",
                1L
        );
        String selectEtfHoldings = sql(
                configuration,
                namespace + "selectEtfHoldings",
                java.util.Map.of("productVersionId", 1L, "limit", 10)
        );
        String resetSavedSimulation = sql(
                configuration,
                namespace + "resetSavedSimulation",
                Map.of(
                        "simulationId", 1L,
                        "expiredAt", LocalDateTime.of(2026, 9, 6, 13, 15),
                        "updatedAt", LocalDateTime.of(2026, 8, 6, 13, 15)
                )
        );

        assertFalse(insertSimulation.contains("age_at_simulation"));
        assertTrue(insertSimulation.contains("previous_gift_amount"));
        assertTrue(insertSimulation.contains("deduction_limit"));
        assertTrue(insertSimulation.contains("deduction_renewal_date"));
        assertTrue(insertSimulation.contains("as_of_date, gift_date, investment_end_date"));
        assertTrue(selectLatestProductDataVersion.contains(
                "kb_product_data_version_id AS product_data_version_id"));
        assertTrue(selectProductDataVersion.contains(
                "kb_product_data_version_id AS product_data_version_id"));
        assertTrue(selectSimulation.contains(
                "s.kb_product_data_version_id AS product_data_version_id"));
        assertTrue(selectSimulation.contains("s.gift_date"));
        assertTrue(selectCompletedGifts.contains("gift_date >= ?"));
        assertTrue(selectCompletedGifts.contains("gift_date <= ?"));
        assertFalse(selectCompletedGifts.contains("gift_date < ?"));
        assertTrue(insertTranche.contains("simulation_tranche ( simul_result_id,"));
        assertTrue(insertTranche.contains("investment_amount, created_at"));
        assertTrue(insertTranche.contains("NOW()"));
        assertTrue(selectTranches.contains("t.simul_result_id AS result_id"));
        assertTrue(selectTranches.contains("t.created_at"));
        assertFalse(selectTranches.contains("t.result_id"));
        assertTrue(insertPortfolio.contains("simulation_portfolio ( simul_result_id,"));
        assertTrue(insertProduct.contains(
                "simulation_product ( simul_portfolio_id,"));
        assertTrue(selectBaseRates.contains(
                "kb_product_version_id AS product_version_id"));
        assertTrue(selectPreferentialRates.contains(
                "kb_product_version_id AS product_version_id"));
        assertFalse(selectPreferentialRates.contains("min_month"));
        assertFalse(selectPreferentialRates.contains("condition_name"));
        assertTrue(selectSelectedPreferentialRates.contains(
                "pir.kb_product_version_id AS product_version_id"));
        assertFalse(selectSelectedPreferentialRates.contains("pir.min_month"));
        assertFalse(selectSelectedPreferentialRates.contains("pir.condition_name"));
        assertTrue(selectEtfHoldings.contains(
                "kb_product_version_id AS product_version_id"));
        assertTrue(selectEtfHoldings.contains("holding_rank"));
        assertFalse(selectEtfHoldings.contains("holding_rank AS `rank`"));
        assertTrue(resetSavedSimulation.contains("status = 'DRAFT'"));
        assertTrue(resetSavedSimulation.contains("selected_portfolio_id = NULL"));
        assertTrue(resetSavedSimulation.contains("saved_at = NULL"));
        assertTrue(resetSavedSimulation.contains("expired_at = ?"));
        assertTrue(resetSavedSimulation.contains("updated_at = ?"));
    }

    private String sql(Configuration configuration, String statement, Object parameter) {
        return configuration.getMappedStatement(statement)
                .getBoundSql(parameter)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Test
    @DisplayName("ETF 위험등급과 우대조건 조회 컬럼은 최종 DDL 계약을 따른다")
    void useFinalProductDataContract() throws Exception {
        String resource = "mapper/simulation/SimulationMapper.xml";
        String xml;

        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("'EX_LOW', 'LOW', 'MEDIUM', 'HIGH'"));
        assertTrue(xml.contains("'EX_LOW', 'LOW', 'MEDIUM'"));
        assertTrue(xml.contains("UPPER(TRIM(pv.product_name)) = 'RISE'"));
        assertTrue(xml.contains("UPPER(TRIM(pv.product_name)) LIKE 'RISE %'"));
        assertFalse(xml.contains("LOW_MEDIUM"));
        assertFalse(xml.contains("MEDIUM_HIGH"));
        assertFalse(xml.contains("condition_name"));
        assertFalse(xml.contains("pir.min_month"));
        assertFalse(xml.contains("pir.max_month"));
        assertFalse(xml.contains("holding_rank AS rank"));
    }

    @Test
    @DisplayName("시뮬레이션은 증여 이력과 공제의 최소 스냅샷 세 값만 저장한다")
    void useMinimalGiftSnapshotContract() throws Exception {
        String resource = "mapper/simulation/SimulationMapper.xml";
        String xml;

        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("previous_gift_amount"));
        assertTrue(xml.contains("deduction_limit"));
        assertTrue(xml.contains("deduction_renewal_date"));
        assertFalse(xml.contains("age_at_simulation"));
        assertFalse(xml.contains("minor_at_simulation"));
        assertFalse(xml.contains("lookback_start_date"));
        assertFalse(xml.contains("used_deduction_amount"));
        assertFalse(xml.contains("remaining_deduction_amount"));
    }

    @Test
    @DisplayName("KB 상품 데이터 버전 ID를 Java 필드명에 맞게 명시적으로 매핑한다")
    void mapProductDataVersionIdExplicitly() throws Exception {
        String resource = "mapper/simulation/SimulationMapper.xml";
        String xml;

        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains(
                "kb_product_data_version_id AS product_data_version_id"
        ));
        assertTrue(xml.contains(
                "s.kb_product_data_version_id AS product_data_version_id"
        ));
    }

    @Test
    @DisplayName("예금과 적금 후보는 전체 운용 기간이 단일 가입 기간을 넘더라도 조회한다")
    void allowDepositAndSavingsCandidatesForReinvestment() throws Exception {
        String resource = "mapper/simulation/SimulationMapper.xml";
        String xml;

        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        String depositQuery = xml.substring(
                xml.indexOf("<select id=\"selectDepositCandidates\""),
                xml.indexOf("<select id=\"selectSavingsCandidates\"")
        );
        String savingsQuery = xml.substring(
                xml.indexOf("<select id=\"selectSavingsCandidates\""),
                xml.indexOf("<select id=\"selectEtfCandidates\"")
        );

        // 재가입 조합이 가능한 상품을 먼저 거르고, 서비스 검증 전에 후보 수를 잘라내지 않는다.
        assertTrue(depositQuery.contains(
                "#{investmentPeriodMonths} &gt;= d.min_month"
        ));
        assertTrue(savingsQuery.contains(
                "#{investmentPeriodMonths} &gt;= s.min_month"
        ));
        assertFalse(depositQuery.contains("CEIL(#{investmentPeriodMonths}"));
        assertFalse(savingsQuery.contains("CEIL(#{investmentPeriodMonths}"));
        assertFalse(depositQuery.contains("LIMIT"));
        assertFalse(savingsQuery.contains("LIMIT"));
        assertTrue(xml.contains(
                "COALESCE(d.min_month, sv.min_month) AS minimum_contract_months"
        ));
        assertTrue(xml.contains(
                "COALESCE(d.max_month, sv.max_month) AS maximum_contract_months"
        ));
    }
}
