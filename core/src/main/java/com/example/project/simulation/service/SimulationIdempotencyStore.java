package com.example.project.simulation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Component
public class SimulationIdempotencyStore {

    static final Duration RETENTION = Duration.ofHours(24);
    static final long MAXIMUM_SIZE = 10_000L;
    private static final int INITIAL_CAPACITY = 100;

    private final Cache<Key, Entry> entries;

    public SimulationIdempotencyStore() {
        this.entries = Caffeine.newBuilder()
                .initialCapacity(INITIAL_CAPACITY)
                .maximumSize(MAXIMUM_SIZE)
                .expireAfterWrite(RETENTION)
                .recordStats()
                .build();
    }

    SimulationIdempotencyStore(Ticker ticker) {
        this.entries = Caffeine.newBuilder()
                .initialCapacity(INITIAL_CAPACITY)
                .maximumSize(MAXIMUM_SIZE)
                .expireAfterWrite(RETENTION)
                .ticker(Objects.requireNonNull(ticker))
                .recordStats()
                .build();
    }

    public <T> Optional<T> find(
            Long userId,
            String operation,
            String idempotencyKey,
            String fingerprint,
            Class<T> responseType
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Entry entry = entries.getIfPresent(
                new Key(userId, operation, idempotencyKey.trim())
        );
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new SimulationException(SimulationError.IDEMPOTENCY_KEY_CONFLICT);
        }
        return Optional.of(responseType.cast(entry.response()));
    }

    public void remember(
            Long userId,
            String operation,
            String idempotencyKey,
            String fingerprint,
            Object response
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        Key key = new Key(userId, operation, idempotencyKey.trim());
        entries.asMap().compute(key, (ignored, existing) -> {
            if (existing != null && !existing.fingerprint().equals(fingerprint)) {
                throw new SimulationException(SimulationError.IDEMPOTENCY_KEY_CONFLICT);
            }
            return existing == null ? new Entry(fingerprint, response) : existing;
        });
    }

    private record Key(Long userId, String operation, String idempotencyKey) {
    }

    private record Entry(String fingerprint, Object response) {
    }
}
