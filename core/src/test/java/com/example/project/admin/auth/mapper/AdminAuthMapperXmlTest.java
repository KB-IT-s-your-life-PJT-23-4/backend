package com.example.project.admin.auth.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuthMapperXmlTest {

    private static final String NAMESPACE =
            "com.example.project.admin.auth.mapper.AdminAuthMapper.";

    @Test
    @DisplayName("관리자 목록은 관리자 역할만 필터링하고 페이지 조건을 적용한다")
    void getAdminsFiltersRolesAndAppliesPagination() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = parameters();
        parameters.put("offset", 20L);
        parameters.put("size", 20);

        String sql = sql(configuration, "getAdmins", parameters);

        assertTrue(sql.contains("WHERE role IN"));
        assertEquals(5, questionMarkCount(sql));
        assertTrue(sql.contains("ORDER BY created_at DESC, user_id DESC"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
        assertFalse(sql.contains("password"));
        assertFalse(sql.contains("phone"));
    }

    @Test
    @DisplayName("관리자 건수 조회는 목록과 동일한 역할 조건을 사용한다")
    void getAdminCountsUsesSameRoleFilter() throws Exception {
        Configuration configuration = configuration();

        String sql = sql(configuration, "getAdminCounts", parameters());

        assertTrue(sql.contains("WHERE role IN"));
        assertEquals(3, questionMarkCount(sql));
        assertFalse(sql.contains("LIMIT"));
        assertFalse(sql.contains("OFFSET"));
    }

    private Map<String, Object> parameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        Set<String> roles = new LinkedHashSet<>();
        roles.add("ROOT");
        roles.add("MIDDLE");
        roles.add("DEFAULT");
        parameters.put("roles", roles);
        return parameters;
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/admin/auth/AdminAuthMapper.xml";
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

    private String sql(
            Configuration configuration,
            String statementId,
            Object parameters
    ) {
        BoundSql boundSql = configuration.getMappedStatement(
                NAMESPACE + statementId
        ).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private int questionMarkCount(String sql) {
        return (int) sql.chars()
                .filter(character -> character == '?')
                .count();
    }
}
