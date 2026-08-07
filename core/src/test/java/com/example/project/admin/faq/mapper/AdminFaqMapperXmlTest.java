package com.example.project.admin.faq.mapper;

import com.example.project.consultation.domain.FaqCategory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminFaqMapperXmlTest {

    private static final String NAMESPACE =
            "com.example.project.admin.faq.mapper.AdminFaqMapper.";

    @Test
    @DisplayName("매퍼 인터페이스의 모든 메서드에 대응하는 XML 구문이 존재한다")
    void allMapperMethodsHaveStatements() throws Exception {
        Configuration configuration = configuration();

        for (Method method : AdminFaqMapper.class.getDeclaredMethods()) {
            assertTrue(
                    configuration.hasStatement(NAMESPACE + method.getName()),
                    method.getName() + "에 대응하는 XML 구문이 없습니다."
            );
        }
    }

    @Test
    @DisplayName("FAQ 목록과 건수 조회에 동일한 카테고리 및 검색어 조건을 적용한다")
    void faqPageAndCountApplySameFilters() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("categoryId", 1L);
        parameters.put("keyword", "신고");
        parameters.put("offset", 20L);
        parameters.put("size", 20);

        String pageSql = sql(configuration, "selectFaqPage", parameters);
        String countSql = sql(configuration, "countFaqs", parameters);

        assertTrue(pageSql.contains("f.faq_category_id2 = ?"));
        assertTrue(pageSql.contains("f.question LIKE CONCAT('%', ?, '%')"));
        assertTrue(pageSql.contains("f.prompt LIKE CONCAT('%', ?, '%')"));
        assertTrue(pageSql.contains("LIMIT ? OFFSET ?"));
        assertTrue(countSql.contains("f.faq_category_id2 = ?"));
        assertTrue(countSql.contains("f.question LIKE CONCAT('%', ?, '%')"));
        assertTrue(countSql.contains("f.prompt LIKE CONCAT('%', ?, '%')"));
        assertFalse(countSql.contains("LIMIT"));
    }

    @Test
    @DisplayName("카테고리 생성은 FaqCategory의 id에 자동 생성 키를 저장한다")
    void createCategoryUsesGeneratedIdProperty() throws Exception {
        Configuration configuration = configuration();
        MappedStatement statement = configuration.getMappedStatement(
                NAMESPACE + "createCategory"
        );

        assertEquals(FaqCategory.class, statement.getParameterMap().getType());
        assertEquals("id", statement.getKeyProperties()[0]);
        assertEquals("faq_category_id", statement.getKeyColumns()[0]);
    }

    @Test
    @DisplayName("FAQ 수정과 삭제는 FAQ ID를 조건으로 사용한다")
    void updateAndDeleteFaqUseFaqId() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = Map.of("id", 10L, "faqId", 10L);

        String updateSql = sql(configuration, "updateFaq", parameters);
        String deleteSql = sql(configuration, "deleteByFaqId", parameters);

        assertTrue(updateSql.contains("WHERE faq_id = ?"));
        assertTrue(deleteSql.contains("WHERE faq_id = ?"));
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/admin/faq/AdminFaqMapper.xml";
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
}
