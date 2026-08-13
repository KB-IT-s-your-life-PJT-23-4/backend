package com.example.project.admin.access.service;

import com.example.project.admin.access.domain.AdminAccessEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminAccessSseServiceTest {

    @Test
    @DisplayName("관리자 한 명의 SSE 연결은 최근 3개만 유지한다")
    void limitsConnectionsPerAdmin() {
        AdminAccessSseService service = new AdminAccessSseService(Runnable::run);

        service.subscribe(1L, null);
        service.subscribe(1L, null);
        service.subscribe(1L, null);
        service.subscribe(1L, null);

        assertEquals(3, service.activeConnectionCount(1L));
    }

    @Test
    @DisplayName("마지막 이벤트 다음에 발생한 이벤트만 재전송 대상으로 선택한다")
    void selectsEventsAfterLastEventIdForReplay() {
        AdminAccessSseService service = new AdminAccessSseService(Runnable::run);

        service.publish(event("event-1"));
        service.publish(event("event-2"));
        service.publish(event("event-3"));

        assertEquals(
                List.of("event-2", "event-3"),
                service.replayEventIdsAfter("event-1")
        );
    }

    @Test
    @DisplayName("재전송 버퍼는 최근 500개 이벤트만 유지한다")
    void limitsReplayBufferSize() {
        AdminAccessSseService service = new AdminAccessSseService(Runnable::run);

        for (int index = 1; index <= 501; index++) {
            service.publish(event("event-" + index));
        }

        assertEquals(500, service.replayBufferSize());
        assertEquals(List.of(), service.replayEventIdsAfter("event-1"));
        assertEquals(
                List.of("event-501"),
                service.replayEventIdsAfter("event-500")
        );
    }

    @Test
    @DisplayName("하트비트 네트워크 전송은 스케줄러가 아닌 SSE 실행기에 위임한다")
    void delegatesHeartbeatToSseExecutor() {
        RecordingExecutor executor = new RecordingExecutor();
        AdminAccessSseService service = new AdminAccessSseService(executor);
        service.subscribe(1L, null);

        service.sendHeartbeat();

        assertEquals(1, executor.taskCount());
    }

    private AdminAccessEvent event(String eventId) {
        return AdminAccessEvent.builder()
                .eventId(eventId)
                .build();
    }

    private static class RecordingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private int taskCount() {
            return tasks.size();
        }
    }
}
