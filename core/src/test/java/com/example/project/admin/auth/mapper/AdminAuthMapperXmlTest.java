package com.example.project.admin.auth.mapper;

import com.example.project.user.domain.UserVO;
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
        assertTrue(sql.contains("phone_encrypted"));
        assertTrue(sql.contains("phone_hmac"));
        assertFalse(sql.matches("(?i).*\\bphone\\b.*"));
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

    @Test
    @DisplayName("관리자 권한 변경은 사용자 ID와 역할을 사용한다")
    void changeAuthUpdatesRoleByUserId() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("userId", 10L);
        parameters.put("role", "MIDDLE");

        String sql = sql(configuration, "changeAuth", parameters);

        assertTrue(sql.contains("UPDATE user SET role = ? WHERE user_id = ?"));
    }

    @Test
    @DisplayName("관리자 삭제는 관리자 역할 계정만 실제 삭제한다")
    void deleteAdminDeletesOnlyAdminRoles() throws Exception {
        Configuration configuration = configuration();

        String sql = sql(
                configuration,
                "deleteAdmin",
                Map.of("userId", 10L)
        );

        assertTrue(sql.startsWith("DELETE FROM user"));
        assertTrue(sql.contains("WHERE user_id = ?"));
        assertTrue(sql.contains("role IN ('ROOT', 'MIDDLE', 'DEFAULT')"));
    }

    @Test
    @DisplayName("관리자 생성은 로그인 필수 정보와 관리자 역할을 저장한다")
    void createAdminInsertsRequiredFields() throws Exception {
        Configuration configuration = configuration();
        UserVO admin = new UserVO();
        admin.setEmail("admin@example.com");
        admin.setPassword("encoded-password");
        admin.setUserName("관리자");
        admin.setPhone("010-1234-5678");
        admin.setRole("DEFAULT");

        String sql = sql(configuration, "createAdmin", admin);

        assertTrue(sql.startsWith("INSERT INTO user"));
        assertTrue(sql.contains("email_encrypted, email_hmac, password, user_name_encrypted, phone_encrypted, phone_hmac, role"));
        assertEquals(7, questionMarkCount(sql));
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
