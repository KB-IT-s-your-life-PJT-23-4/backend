package com.example.project.batch.pii;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiMigrationMapperXmlTest {

    @Test
    void mapperMethodsHaveExplicitStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/pii/PiiMigrationMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()
            ).parse();
        }

        Set<String> methods = Arrays.stream(PiiMigrationMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "selectUsersAfter", "selectFamiliesAfter",
                "countEmailHmacCollision", "countPhoneHmacCollision",
                "updateUser", "updateFamily"
        ), methods);
        methods.forEach(method -> assertTrue(configuration.hasStatement(
                "com.example.project.batch.pii.PiiMigrationMapper." + method
        )));
    }
}
