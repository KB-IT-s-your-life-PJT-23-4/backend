package com.example.project.admin.access.service;

import com.example.project.admin.access.domain.AdminAccessEvent;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Log4j2
@Service
public class AdminAccessSseService {

    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;
    private static final int MAX_CONNECTIONS_PER_ADMIN = 3;
    private static final int REPLAY_BUFFER_SIZE = 500;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<Long, LinkedHashSet<String>> connectionIdsByAdmin = new ConcurrentHashMap<>();
    private final Deque<AdminAccessEvent> replayBuffer = new ArrayDeque<>();
    private final Object eventLock = new Object();
    private final Object connectionLock = new Object();
    private final Object subscriptionLock = new Object();
    private final Executor adminAccessSseExecutor;

    public AdminAccessSseService(
            @Qualifier("adminAccessSseExecutor") Executor adminAccessSseExecutor
    ) {
        this.adminAccessSseExecutor = adminAccessSseExecutor;
    }

    public SseEmitter subscribe(Long adminId, String lastEventId) {
        validateAdminId(adminId);

        String connectionId = adminId + ":" + UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        registerLifecycleCallbacks(adminId, connectionId, emitter);

        synchronized (subscriptionLock) {
            /*
             * 재전송 대상 계산, 재전송, 신규 연결 등록을 publish의 대상 캡처와 같은 락에서
             * 처리하여 재연결 순간의 이벤트가 누락되거나 중복 전송되지 않게 합니다.
             */
            synchronized (eventLock) {
                if (!sendConnectedEvent(connectionId, emitter)) {
                    return emitter;
                }

                if (!sendReplayEvents(connectionId, emitter, lastEventId)) {
                    return emitter;
                }

                evictOldestConnectionIfNecessary(adminId);
                emitters.put(connectionId, emitter);
                addConnection(adminId, connectionId);
            }
        }

        return emitter;
    }

    public void publish(AdminAccessEvent event) {
        if (event == null) {
            return;
        }

        List<Map.Entry<String, SseEmitter>> recipients;
        synchronized (eventLock) {
            appendReplayEvent(event);
            recipients = new ArrayList<>(emitters.entrySet());
        }

        if (recipients.isEmpty()) {
            return;
        }

        executeSafely(() -> broadcast(event, recipients), "접근 로그");
    }

    @Scheduled(fixedDelay = 20000L)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        List<Map.Entry<String, SseEmitter>> recipients =
                new ArrayList<>(emitters.entrySet());

        /*
         * 스케줄러 스레드에서는 전송 작업을 등록만 하고 실제 네트워크 쓰기는
         * SSE 실행기에서 수행합니다.
         */
        executeSafely(() -> broadcastHeartbeat(recipients), "하트비트");
    }

    private void broadcast(
            AdminAccessEvent event,
            List<Map.Entry<String, SseEmitter>> recipients
    ) {
        recipients.forEach(entry ->
                sendEvent(entry.getKey(), entry.getValue(), event));
    }

    private void broadcastHeartbeat(
            List<Map.Entry<String, SseEmitter>> recipients
    ) {
        recipients.forEach(entry ->
                sendHeartbeat(entry.getKey(), entry.getValue()));
    }

    private boolean sendReplayEvents(
            String connectionId,
            SseEmitter emitter,
            String lastEventId
    ) {
        ReplayEvents replayEvents = findReplayEvents(lastEventId);

        if (!replayEvents.isAvailable()) {
            return sendReplayUnavailableEvent(connectionId, emitter);
        }

        for (AdminAccessEvent event : replayEvents.getEvents()) {
            if (!sendEvent(connectionId, emitter, event)) {
                return false;
            }
        }

        return true;
    }

    private ReplayEvents findReplayEvents(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return ReplayEvents.available(List.of());
        }

        List<AdminAccessEvent> events = new ArrayList<>(replayBuffer);
        for (int index = 0; index < events.size(); index++) {
            if (lastEventId.equals(events.get(index).getEventId())) {
                return ReplayEvents.available(
                        new ArrayList<>(events.subList(index + 1, events.size()))
                );
            }
        }

        return ReplayEvents.unavailable();
    }

    private void appendReplayEvent(AdminAccessEvent event) {
        replayBuffer.addLast(event);

        while (replayBuffer.size() > REPLAY_BUFFER_SIZE) {
            replayBuffer.removeFirst();
        }
    }

    private boolean sendEvent(
            String connectionId,
            SseEmitter emitter,
            AdminAccessEvent event
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.getEventId())
                    .name("access-log")
                    .data(event));
            return true;
        } catch (IOException | IllegalStateException exception) {
            remove(connectionId);
            return false;
        }
    }

    private boolean sendConnectedEvent(String connectionId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("connectionId", connectionId)));
            return true;
        } catch (IOException | IllegalStateException exception) {
            remove(connectionId);
            return false;
        }
    }

    private boolean sendReplayUnavailableEvent(
            String connectionId,
            SseEmitter emitter
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .name("replay-unavailable")
                    .data(Map.of(
                            "message", "재전송 가능 범위를 벗어났습니다. 최신 목록을 다시 조회해 주세요."
                    )));
            return true;
        } catch (IOException | IllegalStateException exception) {
            remove(connectionId);
            return false;
        }
    }

    private void sendHeartbeat(String connectionId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .comment("keep-alive"));
        } catch (IOException | IllegalStateException exception) {
            remove(connectionId);
        }
    }

    private void executeSafely(Runnable task, String taskName) {
        try {
            adminAccessSseExecutor.execute(task);
        } catch (RejectedExecutionException exception) {
            log.warn("관리자 SSE {} 전송 작업이 실행기 용량을 초과했습니다.", taskName);
        }
    }

    private void registerLifecycleCallbacks(
            Long adminId,
            String connectionId,
            SseEmitter emitter
    ) {
        emitter.onCompletion(() -> remove(adminId, connectionId));
        emitter.onTimeout(() -> {
            remove(adminId, connectionId);
            emitter.complete();
        });
        emitter.onError(exception -> remove(adminId, connectionId));
    }

    private void evictOldestConnectionIfNecessary(Long adminId) {
        SseEmitter evictedEmitter = null;

        synchronized (connectionLock) {
            LinkedHashSet<String> connectionIds = connectionIdsByAdmin.get(adminId);
            if (connectionIds != null
                    && connectionIds.size() >= MAX_CONNECTIONS_PER_ADMIN) {
                String oldestConnectionId = connectionIds.iterator().next();
                connectionIds.remove(oldestConnectionId);
                evictedEmitter = emitters.remove(oldestConnectionId);
            }
        }

        if (evictedEmitter != null) {
            evictedEmitter.complete();
        }
    }

    private void addConnection(Long adminId, String connectionId) {
        synchronized (connectionLock) {
            connectionIdsByAdmin
                    .computeIfAbsent(adminId, ignored -> new LinkedHashSet<>())
                    .add(connectionId);
        }
    }

    private void remove(String connectionId) {
        Long adminId = extractAdminId(connectionId);
        remove(adminId, connectionId);
    }

    private void remove(Long adminId, String connectionId) {
        emitters.remove(connectionId);

        synchronized (connectionLock) {
            LinkedHashSet<String> connectionIds = connectionIdsByAdmin.get(adminId);
            if (connectionIds == null) {
                return;
            }

            connectionIds.remove(connectionId);
            if (connectionIds.isEmpty()) {
                connectionIdsByAdmin.remove(adminId);
            }
        }
    }

    private Long extractAdminId(String connectionId) {
        int separatorIndex = connectionId.indexOf(':');
        return Long.valueOf(connectionId.substring(0, separatorIndex));
    }

    private void validateAdminId(Long adminId) {
        if (adminId == null || adminId <= 0) {
            throw new IllegalArgumentException("관리자 ID가 올바르지 않습니다.");
        }
    }

    int activeConnectionCount(Long adminId) {
        synchronized (connectionLock) {
            LinkedHashSet<String> connectionIds = connectionIdsByAdmin.get(adminId);
            return connectionIds == null ? 0 : connectionIds.size();
        }
    }

    int replayBufferSize() {
        synchronized (eventLock) {
            return replayBuffer.size();
        }
    }

    List<String> replayEventIdsAfter(String lastEventId) {
        synchronized (eventLock) {
            ReplayEvents replayEvents = findReplayEvents(lastEventId);
            if (!replayEvents.isAvailable()) {
                return List.of();
            }

            return replayEvents.getEvents().stream()
                    .map(AdminAccessEvent::getEventId)
                    .toList();
        }
    }

    private static class ReplayEvents {

        private final boolean available;
        private final List<AdminAccessEvent> events;

        private ReplayEvents(boolean available, List<AdminAccessEvent> events) {
            this.available = available;
            this.events = events;
        }

        private static ReplayEvents available(List<AdminAccessEvent> events) {
            return new ReplayEvents(true, events);
        }

        private static ReplayEvents unavailable() {
            return new ReplayEvents(false, List.of());
        }

        private boolean isAvailable() {
            return available;
        }

        private List<AdminAccessEvent> getEvents() {
            return events;
        }
    }
}
