package com.example.project.consultation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class AIOtherIntentCounter {

    private static final int TRIGGER_COUNT = 11;

    private final Cache<Key, CounterState> counters =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofDays(2))
                    .build();

    public CounterSnapshot increment(Long userId, LocalDateTime occurredAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 null일 수 없습니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 null일 수 없습니다.");
        }

        Key key = Key.builder()
                .userId(userId)
                .date(occurredAt.toLocalDate())
                .build();

        CounterState state = counters.asMap()
                .compute(key, (ignored, existing) -> {
                    if (existing == null) {
                        return new CounterState(
                                1,
                                occurredAt,
                                null
                        );
                    }

                    int nextCount = existing.getCount() + 1;

                    LocalDateTime thresholdReachedAt = existing.getThresholdReachedAt();

                    if (thresholdReachedAt == null && nextCount >= TRIGGER_COUNT) {
                        thresholdReachedAt = occurredAt;
                    }

                    return new CounterState(
                            nextCount,
                            existing.getFirstDetectedAt(),
                            thresholdReachedAt
                    );
                });

        return CounterSnapshot.builder()
                .count(state.getCount())
                .firstDetectedAt(state.getFirstDetectedAt())
                .thresholdReachedAt(state.getThresholdReachedAt())
                .build();
    }

    @Data
    @EqualsAndHashCode
    @Builder
    @AllArgsConstructor
    private static final class Key{
        private final Long userId;
        private final LocalDate date;
    }

    @Data
    @AllArgsConstructor
    @Builder
    private static final class CounterState{
        private final int count;
        private final LocalDateTime firstDetectedAt;
        private final LocalDateTime thresholdReachedAt;
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static final class CounterSnapshot{
        private final int count;
        private final LocalDateTime firstDetectedAt;
        private final LocalDateTime thresholdReachedAt;
    }
}
