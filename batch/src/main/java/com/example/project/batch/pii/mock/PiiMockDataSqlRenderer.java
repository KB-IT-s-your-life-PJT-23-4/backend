package com.example.project.batch.pii.mock;

import com.example.project.user.crypto.UserPiiCryptoService;
import com.example.project.user.crypto.UserPiiField;
import com.example.project.user.crypto.UserPiiHmacService;
import lombok.RequiredArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class PiiMockDataSqlRenderer {

    private static final Pattern TOKEN = Pattern.compile(
            "\\{\\{PII:(AES|HMAC):(EMAIL|PHONE|USER_NAME|FAMILY_NAME|FAMILY_BIRTH_DATE):([^{}]+)}}"
    );
    private static final String RENDER_GUARD = "{{KAN265_RENDERED}}";

    private final UserPiiCryptoService cryptoService;
    private final UserPiiHmacService hmacService;

    public RenderedSql render(String template) {
        if (template == null || !template.contains(RENDER_GUARD)) {
            throw new IllegalArgumentException("KAN-265 render guard is missing");
        }

        Matcher matcher = TOKEN.matcher(template);
        StringBuilder output = new StringBuilder(template.length() + 4096);
        int tokenCount = 0;
        while (matcher.find()) {
            String operation = matcher.group(1);
            UserPiiField field = UserPiiField.valueOf(matcher.group(2));
            String plainValue = matcher.group(3);
            String protectedValue = protect(operation, field, plainValue);
            matcher.appendReplacement(output, Matcher.quoteReplacement(protectedValue));
            tokenCount++;
        }
        matcher.appendTail(output);

        if (tokenCount == 0 || TOKEN.matcher(output).find()) {
            throw new IllegalStateException("KAN-265 PII tokens were not fully rendered");
        }
        String rendered = output.toString().replace(RENDER_GUARD, "1");
        return new RenderedSql(rendered, tokenCount);
    }

    private String protect(String operation, UserPiiField field, String plainValue) {
        if ("AES".equals(operation)) {
            return cryptoService.encrypt(plainValue, field);
        }
        return switch (field) {
            case EMAIL -> hmacService.email(plainValue);
            case PHONE -> hmacService.phone(plainValue);
            default -> throw new IllegalArgumentException(
                    "HMAC is only supported for email and phone mock-data tokens"
            );
        };
    }

    public record RenderedSql(String sql, int tokenCount) {
    }
}
