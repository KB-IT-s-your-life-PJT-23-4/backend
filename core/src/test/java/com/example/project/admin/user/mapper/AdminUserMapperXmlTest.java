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

        assertEquals(
                Set.of("countUsers", "selectUsers", "selectUserById", "blockUser", "unblockUser"),
                methodNames
        );
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

    @Test
    @DisplayName("회원 목록과 전체 건수는 일반 회원만 조회한다")
    void listAndCountOnlyGeneralUsers() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 7L);
        parameters.put("email", "test@example.com");
        parameters.put("name", "홍길동");
        parameters.put("offset", 0L);
        parameters.put("size", 20);

        String countSql = sql("countUsers", parameters);
        String selectSql = sql("selectUsers", parameters);

        assertTrue(countSql.contains("WHERE u.role = 'USER'"));
        assertTrue(selectSql.contains("WHERE u.role = 'USER'"));
        assertTrue(countSql.contains("u.user_id = ?"));
        assertTrue(selectSql.contains("u.user_id = ?"));
    }

    @Test
    @DisplayName("차단과 해제 SQL은 일반 회원과 현재 계정 상태를 조건으로 갱신한다")
    void mutateOnlyExpectedUserState() {
        Map<String, Object> blockParameters = new HashMap<>();
        blockParameters.put("userId", 7L);
        blockParameters.put("blockedUntil", java.time.LocalDateTime.of(2026, 8, 31, 23, 59));

        String blockSql = sql("blockUser", blockParameters);
        String unblockSql = sql("unblockUser", Map.of("userId", 7L));

        assertTrue(blockSql.contains("role = 'USER'"));
        assertTrue(blockSql.contains("account_status = 'ACTIVE'"));
        assertTrue(blockSql.contains("blocked_until IS NULL"));
        assertTrue(blockSql.contains("? > CURRENT_TIMESTAMP"));
        assertTrue(unblockSql.contains("role = 'USER'"));
        assertTrue(unblockSql.contains("account_status = 'BLOCKED'"));
        assertTrue(unblockSql.contains("blocked_until = NULL"));
    }

    private String sql(String statementId, Object parameters) {
        return configuration.getMappedStatement(NAMESPACE + statementId)
                .getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
