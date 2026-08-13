package com.example.project.batch.pii;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;

public final class PiiMigrationApplication {

    private static final Logger log = LogManager.getLogger(PiiMigrationApplication.class);
    private static final String CONFIRMATION = "--confirm-kan-265-pii-migration";
    private static final int DEFAULT_BATCH_SIZE = 100;

    private PiiMigrationApplication() {
    }

    public static void main(String[] args) {
        if (Arrays.stream(args).noneMatch(CONFIRMATION::equals)) {
            throw new IllegalArgumentException(
                    "explicit confirmation argument is required: " + CONFIRMATION
            );
        }

        int batchSize = Arrays.stream(args)
                .filter(argument -> argument.startsWith("--batch-size="))
                .findFirst()
                .map(argument -> Integer.parseInt(argument.substring("--batch-size=".length())))
                .orElse(DEFAULT_BATCH_SIZE);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(PiiMigrationConfig.class)) {
            PiiMigrationReport report = context.getBean(PiiMigrationService.class).migrate(batchSize);
            log.info(
                    "KAN-265 migration completed: userScanned={}, userMigrated={}, userVerified={}, userFailed={}, "
                            + "familyScanned={}, familyMigrated={}, familyVerified={}, familyFailed={}",
                    report.userScanned(), report.userMigrated(), report.userVerified(), report.userFailed(),
                    report.familyScanned(), report.familyMigrated(), report.familyVerified(), report.familyFailed()
            );
        }
    }
}
