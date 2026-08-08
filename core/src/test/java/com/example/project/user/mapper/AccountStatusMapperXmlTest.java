package com.example.project.user.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStatusMapperXmlTest {

    @Test
    @DisplayName("만료 차단 해제 SQL은 사용자와 만료 상태를 모두 조건으로 사용한다")
    void activateExpiredBlockWithConcurrencySafeCondition() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/user/AccountStatusMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }

        String statement = "com.example.project.user.mapper.AccountStatusMapper.activateExpiredBlock";
        String sql = configuration.getMappedStatement(statement)
                .getBoundSql(Map.of("userId", 7L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("account_status = 'ACTIVE'"));
        assertTrue(sql.contains("blocked_until = NULL"));
        assertTrue(sql.contains("user_id = ?"));
        assertTrue(sql.contains("account_status = 'BLOCKED'"));
        assertTrue(sql.contains("blocked_until <= CURRENT_TIMESTAMP"));
    }
}
