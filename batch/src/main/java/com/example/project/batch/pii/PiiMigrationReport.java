package com.example.project.batch.pii;

public record PiiMigrationReport(
        long userScanned,
        long userMigrated,
        long userVerified,
        long userFailed,
        long familyScanned,
        long familyMigrated,
        long familyVerified,
        long familyFailed
) {
}
