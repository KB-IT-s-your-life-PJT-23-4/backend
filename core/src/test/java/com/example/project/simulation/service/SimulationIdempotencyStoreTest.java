package com.example.project.simulation.service;

import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationIdempotencyStoreTest {

    private static final Long USER_ID = 1L;
    private static final String OPERATION = "POST:/api/gs";
    private static final String IDEMPOTENCY_KEY = "test-key";
    private static final String FINGERPRINT = "request-a";

    @Test
    void returnsRememberedResponseWithinRetention() {
        MutableTicker ticker = new MutableTicker();
        SimulationIdempotencyStore store = new SimulationIdempotencyStore(ticker);
        store.remember(
                USER_ID,
                OPERATION,
                IDEMPOTENCY_KEY,
                FINGERPRINT,
                "response"
        );

        ticker.advance(SimulationIdempotencyStore.RETENTION.minusMinutes(1));

        assertEquals(
                Optional.of("response"),
                store.find(
                        USER_ID,
                        OPERATION,
                        IDEMPOTENCY_KEY,
                        FINGERPRINT,
                        String.class
                )
        );
    }

    @Test
    void expiresResponseAfterOneHour() {
        MutableTicker ticker = new MutableTicker();
        SimulationIdempotencyStore store = new SimulationIdempotencyStore(ticker);
        store.remember(
                USER_ID,
                OPERATION,
                IDEMPOTENCY_KEY,
                FINGERPRINT,
                "response"
        );

        ticker.advance(SimulationIdempotencyStore.RETENTION);

        assertTrue(store.find(
                USER_ID,
                OPERATION,
                IDEMPOTENCY_KEY,
                FINGERPRINT,
                String.class
        ).isEmpty());
    }

    @Test
    void rejectsDifferentRequestUsingSameKey() {
        SimulationIdempotencyStore store = new SimulationIdempotencyStore(
                new MutableTicker()
        );
        store.remember(
                USER_ID,
                OPERATION,
                IDEMPOTENCY_KEY,
                FINGERPRINT,
                "response"
        );

        SimulationException exception = assertThrows(
                SimulationException.class,
                () -> store.find(
                        USER_ID,
                        OPERATION,
                        IDEMPOTENCY_KEY,
                        "request-b",
                        String.class
                )
        );

        assertEquals(
                SimulationError.IDEMPOTENCY_KEY_CONFLICT,
                exception.getError()
        );
    }

    private static final class MutableTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        private void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
