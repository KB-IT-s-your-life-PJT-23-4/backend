package com.example.project.batch.pii.mock;

import com.example.project.user.crypto.UserPiiCryptoService;
import com.example.project.user.crypto.UserPiiField;
import com.example.project.user.crypto.UserPiiHmacService;
import com.example.project.user.crypto.UserPiiKeyProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiMockDataSqlRendererTest {

    @Test
    void rendersAllTokensWithoutExposingPlainValues() {
        UserPiiKeyProvider keys = new UserPiiKeyProvider("v1", key('a'), key('b'));
        UserPiiCryptoService crypto = new UserPiiCryptoService(keys);
        UserPiiHmacService hmac = new UserPiiHmacService(keys);
        PiiMockDataSqlRenderer renderer = new PiiMockDataSqlRenderer(crypto, hmac);
        String template = """
                SET @kan265_rendered = {{KAN265_RENDERED}};
                '{{PII:AES:EMAIL: User@Example.com }}',
                '{{PII:HMAC:EMAIL: User@Example.com }}',
                '{{PII:AES:FAMILY_BIRTH_DATE:2008-02-29}}'
                """;

        PiiMockDataSqlRenderer.RenderedSql rendered = renderer.render(template);

        assertEquals(3, rendered.tokenCount());
        assertTrue(rendered.sql().contains("SET @kan265_rendered = 1;"));
        assertFalse(rendered.sql().contains("User@Example.com"));
        assertFalse(rendered.sql().contains("2008-02-29"));
        assertTrue(rendered.sql().contains(hmac.email(" User@Example.com ")));
        String encryptedEmail = quotedValue(rendered.sql(), 0);
        assertEquals(" User@Example.com ", crypto.decrypt(encryptedEmail, UserPiiField.EMAIL));
        assertNotEquals(encryptedEmail, quotedValue(renderer.render(template).sql(), 0));
    }

    @Test
    void rejectsMissingGuardAndUnsupportedHmacField() {
        UserPiiKeyProvider keys = new UserPiiKeyProvider("v1", key('a'), key('b'));
        PiiMockDataSqlRenderer renderer = new PiiMockDataSqlRenderer(
                new UserPiiCryptoService(keys), new UserPiiHmacService(keys)
        );

        assertThrows(IllegalArgumentException.class, () -> renderer.render("plain sql"));
        assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render("{{KAN265_RENDERED}} {{PII:HMAC:USER_NAME:Name}}")
        );
    }

    private String quotedValue(String sql, int index) {
        String[] values = sql.lines()
                .filter(line -> line.startsWith("'"))
                .toArray(String[]::new);
        String line = values[index];
        return line.substring(1, line.lastIndexOf('\''));
    }

    private String key(char value) {
        return Base64.getEncoder().encodeToString(
                String.valueOf(value).repeat(32).getBytes(StandardCharsets.UTF_8)
        );
    }
}
