package com.example.project.simulation.mapper;

import com.example.project.simulation.domain.SimulationStatus;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationHistoryMapperXmlTest {

    private static final String NAMESPACE =
            "com.example.project.simulation.mapper.SimulationMapper.";

    @Test
    @DisplayName("이력 목록에서 만료된 DRAFT를 제외하고 최신 변경 순으로 조회한다")
    void excludeExpiredDraftAndOrderByLatestUpdate() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 1L);
        parameters.put("status", SimulationStatus.DRAFT);
        parameters.put("familyId", 31L);
        parameters.put("now", LocalDateTime.of(2026, 8, 6, 12, 0));
        parameters.put("offset", 0L);
        parameters.put("size", 10);

        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + "selectSimulationPage"
        ).getBoundSql(parameters);
        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains(
                "s.status = 'SAVED' OR (s.status = 'DRAFT' AND s.expired_at > ?)"
        ));
        assertTrue(sql.contains("AND s.status = ?"));
        assertTrue(sql.contains("AND s.family_id = ?"));
        assertTrue(sql.contains("ORDER BY s.updated_at DESC, s.simul_id DESC"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
    }

    @Test
    @DisplayName("이력 목록의 총 개수에도 동일한 만료 및 필터 조건을 적용한다")
    void applySameFiltersToHistoryCount() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 1L);
        parameters.put("status", SimulationStatus.SAVED);
        parameters.put("familyId", null);
        parameters.put("now", LocalDateTime.of(2026, 8, 6, 12, 0));

        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + "countSimulations"
        ).getBoundSql(parameters);
        String sql = normalize(boundSql.getSql());

        assertTrue(sql.contains(
                "s.status = 'SAVED' OR (s.status = 'DRAFT' AND s.expired_at > ?)"
        ));
        assertTrue(sql.contains("AND s.status = ?"));
    }

    private Configuration configuration() throws Exception {
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
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
