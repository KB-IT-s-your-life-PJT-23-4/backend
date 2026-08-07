package com.example.project.admin.user.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminUserMapperXmlTest {

    private static final String NAMESPACE =
            "com.example.project.admin.user.mapper.AdminUserMapper.";

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/admin/user/AdminUserMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }
    }

    @Test
    @DisplayName("Mapper 인터페이스와 XML statement ID가 일치한다")
    void matchMapperMethodsAndStatements() {
        Set<String> methodNames = Arrays.stream(AdminUserMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("countUsers", "selectUsers", "selectUserById"), methodNames);
        methodNames.forEach(name -> assertTrue(configuration.hasStatement(NAMESPACE + name)));
    }

    @Test
    @DisplayName("검색·집계·페이지 SQL은 기존 회원 관계 테이블만 읽는다")
    void readExistingTablesWithoutMutation() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 7L);
        parameters.put("email", "test@example.com");
        parameters.put("name", "홍길동");
        parameters.put("offset", 20L);
        parameters.put("size", 20);

        String sql = sql("selectUsers", parameters);

        assertTrue(sql.contains("u.user_id = ?"));
        assertTrue(sql.contains("LOWER(u.email) LIKE CONCAT('%', ?, '%')"));
        assertTrue(sql.contains("u.user_name LIKE CONCAT('%', ?, '%')"));
        assertTrue(sql.contains("COUNT(DISTINCT f.family_id) AS recipient_count"));
        assertTrue(sql.contains("COUNT(DISTINCT g.gift_id) AS gift_count"));
        assertTrue(sql.contains("COUNT(DISTINCT s.simul_id) AS simulation_count"));
        assertTrue(sql.endsWith("LIMIT ? OFFSET ?"));
        assertFalse(sql.matches("(?is).*\\b(INSERT|UPDATE|DELETE)\\b.*"));
    }

    private String sql(String statementId, Object parameters) {
        return configuration.getMappedStatement(NAMESPACE + statementId)
                .getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
