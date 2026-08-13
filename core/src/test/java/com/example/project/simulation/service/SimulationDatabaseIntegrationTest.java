package com.example.project.simulation.service;

import com.example.project.simulation.domain.BaseRateRecord;
import com.example.project.simulation.domain.EtfHoldingRecord;
import com.example.project.simulation.domain.PreferentialRateRecord;
import com.example.project.simulation.domain.ProductCandidate;
import com.example.project.simulation.domain.ProductDataVersionRecord;
import com.example.project.simulation.domain.SimulationRecord;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.dto.response.SimulationResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.mapper.SimulationMapper;
import com.example.project.user.mapper.UserMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "mirizoom.integration", matches = "true")
class SimulationDatabaseIntegrationTest {

    @Test
    @DisplayName("0.5.8 목 데이터의 DRAFT와 SAVED 단건 결과를 실제 DB에서 조회한다")
    void readSimulationMockDataFromLocalDatabase() throws Exception {
        try (HikariDataSource dataSource = dataSource()) {
            SqlSessionFactory sessionFactory = sessionFactory(dataSource);
            try (SqlSession session = sessionFactory.openSession()) {
                SimulationService service = new SimulationService(
                        session.getMapper(SimulationMapper.class),
                        session.getMapper(UserMapper.class),
                        new SimulationCalculator(),
                        new SimulationIdempotencyStore(),
                        new EtfVolatilityCalculator()
                );

                SimulationResponse draft = service.get(990001L, 990001L);
                assertEquals(SimulationStatus.DRAFT, draft.status());
                assertEquals(980001L,
                        draft.productDataVersion().productDataVersionId());
                assertEquals(2, draft.results().size());
                assertEquals(3, draft.results().stream()
                        .mapToInt(result -> result.tranches().size()).sum());
                assertEquals(3, draft.recommendations().size());
                assertNull(draft.selection());
                assertEquals(20_000_000L,
                        draft.giftHistorySummary().previousGiftAmount());

                SimulationResponse saved = service.get(990002L, 990001L);
                assertEquals(SimulationStatus.SAVED, saved.status());
                assertEquals(2, saved.results().size());
                assertEquals(3, saved.results().stream()
                        .mapToInt(result -> result.tranches().size()).sum());
                assertEquals(3, saved.recommendations().size());
                assertEquals(992011L, saved.selection().selectedPortfolioId());
                assertEquals(3, saved.selection().selectedProducts().size());
                assertEquals(20_000_000L,
                        saved.giftHistorySummary().previousGiftAmount());

                SimulationException expired = assertThrows(
                        SimulationException.class,
                        () -> service.get(990004L, 990001L)
                );
                assertEquals(
                        SimulationError.SIMULATION_EXPIRED,
                        expired.getError()
                );

                SimulationMapper mapper = session.getMapper(SimulationMapper.class);
                SimulationRecord simulation = mapper.selectSimulation(990001L);
                assertEquals(980001L, simulation.getProductDataVersionId());

                ProductDataVersionRecord dataVersion =
                        mapper.selectProductDataVersion(980001L);
                assertEquals(980001L, dataVersion.getProductDataVersionId());

                List<BaseRateRecord> baseRates = mapper.selectBaseRates(981001L);
                assertEquals(981001L, baseRates.get(0).getProductVersionId());

                List<PreferentialRateRecord> preferentialRates =
                        mapper.selectPreferentialRates(981001L);
                assertEquals(981001L,
                        preferentialRates.get(0).getProductVersionId());

                List<EtfHoldingRecord> holdings =
                        mapper.selectEtfHoldings(981003L, 10);
                assertEquals(981003L, holdings.get(0).getProductVersionId());

                // 재가입 회차마다 해당 가입기간의 금리 구간이 존재하는지 검증한다.
                assertCandidateRateTiersCoverContractTerms(
                        mapper,
                        mapper.selectDepositCandidates(980001L, 36),
                        36
                );
                assertCandidateRateTiersCoverContractTerms(
                        mapper,
                        mapper.selectSavingsCandidates(980001L, 36),
                        36
                );
            }
        }
    }

    private void assertCandidateRateTiersCoverContractTerms(
            SimulationMapper mapper,
            List<ProductCandidate> candidates,
            int investmentPeriodMonths
    ) {
        assertFalse(candidates.isEmpty());
        SimulationCalculator calculator = new SimulationCalculator();
        for (ProductCandidate candidate : candidates) {
            List<Integer> periods = calculator.reinvestmentPeriods(
                    investmentPeriodMonths,
                    candidate.getMinMonth(),
                    candidate.getMaxMonth()
            );
            assertFalse(periods.isEmpty());
            List<BaseRateRecord> tiers = mapper.selectBaseRates(candidate.getProductVersionId());
            for (Integer period : periods) {
                assertFalse(tiers.stream()
                        .filter(rate -> rate.getMinimumMonths() <= period)
                        .filter(rate -> rate.getMaximumMonths() == null
                                || rate.getMaximumMonths() >= period)
                        .toList()
                        .isEmpty());
            }
        }
    }

    private HikariDataSource dataSource() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = new ClassPathResource(
                "database.properties").getInputStream()) {
            properties.load(input);
        }
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(properties.getProperty("jdbc.driver"));
        config.setJdbcUrl(properties.getProperty("jdbc.url"));
        config.setUsername(properties.getProperty("jdbc.username"));
        config.setPassword(properties.getProperty("jdbc.password"));
        config.setReadOnly(true);
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }

    private SqlSessionFactory sessionFactory(HikariDataSource dataSource)
            throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources(
                        "classpath:mapper/**/*Mapper.xml")
        );
        return factory.getObject();
    }
}
