package com.example.project.batch.pii.mock;

import com.example.project.user.crypto.UserPiiCryptoService;
import com.example.project.user.crypto.UserPiiHmacService;
import com.example.project.user.crypto.UserPiiKeyProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public final class PiiMockDataSqlApplication {

    private static final Path DEFAULT_INPUT = Path.of(
            "..", "database", "API_TEST_MOCK_DATA_20260812.sql"
    );
    private static final Path DEFAULT_OUTPUT = Path.of(
            "build", "generated-sql", "API_TEST_MOCK_DATA_20260812_DDL_0.9.1.sql"
    );

    private PiiMockDataSqlApplication() {
    }

    public static void main(String[] args) throws IOException {
        Path input = argument(args, "--input=")
                .map(Path::of)
                .orElse(DEFAULT_INPUT)
                .toAbsolutePath()
                .normalize();
        Path output = argument(args, "--output=")
                .map(Path::of)
                .orElse(DEFAULT_OUTPUT)
                .toAbsolutePath()
                .normalize();
        if (input.equals(output)) {
            throw new IllegalArgumentException("input template cannot be overwritten");
        }
        String activeKeyId = argument(args, "--active-key-id=").orElse("v1");

        UserPiiKeyProvider keys = new UserPiiKeyProvider(
                activeKeyId,
                requiredEnvironment("AI_CONVERSATION_KEY_V1"),
                environment("AI_CONVERSATION_KEY_V2"),
                requiredEnvironment("PII_HMAC_KEY_V1")
        );
        PiiMockDataSqlRenderer renderer = new PiiMockDataSqlRenderer(
                new UserPiiCryptoService(keys),
                new UserPiiHmacService(keys)
        );

        String template = Files.readString(input, StandardCharsets.UTF_8);
        PiiMockDataSqlRenderer.RenderedSql rendered = renderer.render(template);
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                output,
                rendered.sql(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
        System.out.printf(
                "KAN-265 mock DML rendered: tokens=%d, output=%s%n",
                rendered.tokenCount(), output
        );
    }

    private static java.util.Optional<String> argument(String[] args, String prefix) {
        return Arrays.stream(args)
                .filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length()))
                .findFirst();
    }

    private static String requiredEnvironment(String name) {
        String value = environment(name);
        if (value.isBlank()) {
            throw new IllegalStateException("required environment variable is missing: " + name);
        }
        return value;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}
