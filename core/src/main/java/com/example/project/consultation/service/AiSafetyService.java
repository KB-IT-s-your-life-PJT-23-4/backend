package com.example.project.consultation.service;

import com.example.project.consultation.dto.fastapi.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiSafetyService {

    private final AiSafetyPersistenceService persistenceService;

    public Mono<Void> process(Long userId, String question, List<String> familyNames, ChatResponse response) {
        if (!isSafetyIntent(response.intent())) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() ->
                persistenceService.process(
                        userId,
                        question,
                        familyNames,
                        response
                )
        )
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }


    private boolean isSafetyIntent(String intent) {
        return "jailbreak".equalsIgnoreCase(intent)
                || "other".equalsIgnoreCase(intent);
    }
}
