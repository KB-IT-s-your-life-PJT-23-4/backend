package com.example.project.simulation.service;

import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationIdempotencyStore {

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

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

        Entry entry = entries.get(new Key(userId, operation, idempotencyKey.trim()));
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
        entries.compute(key, (ignored, existing) -> {
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
