package com.example.project.consultation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class AIOtherIntentCounter {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Cache<Key, Integer> counters =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofDays(2))
                    .build();

    public int increment(Long userId) {
        LocalDate date = LocalDate.now(SERVICE_ZONE);
        Key key = Key.builder()
                .userId(userId)
                .date(date)
                .build();

        return counters.asMap().merge(
                key,
                1,
                Integer::sum
        );
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private class Key{
        Long userId;
        LocalDate date;
    }
}
