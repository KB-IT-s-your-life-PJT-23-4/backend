package com.example.project.simulation.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

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
}
