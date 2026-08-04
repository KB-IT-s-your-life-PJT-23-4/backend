package com.example.project.simulation.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertFalse(xml.contains("LOW_MEDIUM"));
        assertFalse(xml.contains("MEDIUM_HIGH"));
        assertFalse(xml.contains("condition_name"));
        assertFalse(xml.contains("pir.min_month"));
        assertFalse(xml.contains("pir.max_month"));
    }
}
