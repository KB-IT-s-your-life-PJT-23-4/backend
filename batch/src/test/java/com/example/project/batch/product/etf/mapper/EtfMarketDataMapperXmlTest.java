package com.example.project.batch.product.etf.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EtfMarketDataMapperXmlTest {

    @Test
    void mapperMethodsHaveExplicitStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/product/EtfMarketDataMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()
            ).parse();
        }

        Arrays.stream(EtfMarketDataMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .forEach(method -> assertTrue(configuration.hasStatement(
                        "com.example.project.batch.product.etf.mapper.EtfMarketDataMapper." + method),
                        "매퍼 SQL이 없습니다: " + method));
    }
}
