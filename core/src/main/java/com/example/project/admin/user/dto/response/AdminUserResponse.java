package com.example.project.admin.user.dto.response;

import com.example.project.admin.user.domain.AdminUserRecord;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminUserResponse {

    private final Long userId;
    private final String email;
    private final String name;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final String role;
    private final String accountStatus;
    private final boolean accountStatusAvailable;
    private final LocalDateTime blockedUntil;
    private final long recipientCount;
    private final long giftCount;
    private final long simulationCount;

    public static AdminUserResponse from(AdminUserRecord record) {
        return new AdminUserResponse(
                record.getUserId(),
                maskEmail(record.getEmail()),
                maskName(record.getUserName()),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                record.getRole(),
                record.getAccountStatus(),
                true,
                record.getBlockedUntil(),
                nonNegative(record.getRecipientCount()),
                nonNegative(record.getGiftCount()),
                nonNegative(record.getSimulationCount())
        );
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return maskName(email);
        }
        String localPart = email.substring(0, separator);
        String domain = email.substring(separator);
        String visible = localPart.substring(0, 1);
        return visible + "***" + domain;
    }

    private static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        int length = name.codePointCount(0, name.length());
        if (length == 1) {
            return "*";
        }
        int firstEnd = name.offsetByCodePoints(0, 1);
        String first = name.substring(0, firstEnd);
        if (length == 2) {
            return first + "*";
        }
        int lastStart = name.offsetByCodePoints(0, length - 1);
        return first + "*".repeat(length - 2) + name.substring(lastStart);
    }

    private static long nonNegative(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }
}
