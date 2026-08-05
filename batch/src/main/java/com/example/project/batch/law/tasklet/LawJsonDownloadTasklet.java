package com.example.project.batch.law.tasklet;

import com.example.project.batch.law.client.LawApiClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 클라이언트 호출해서 파일 저장(원본 json 저장)
@Component
@Log4j2
public class LawJsonDownloadTasklet implements Tasklet {

    private final LawApiClient lawApiClient;
    private final List<String> lawIds;
    private final Path jsonDir;

    public LawJsonDownloadTasklet(LawApiClient lawApiClient,
                                  @Value("${law.api.law-id-list}") String lawIdList,
                                  @Value("${law.json.dir}") String jsonDir) {
        this.lawApiClient = lawApiClient;
        this.lawIds = Arrays.stream(lawIdList.split(",")).map(String::trim).filter(id -> !id.isEmpty()).collect(Collectors.toList());
        this.jsonDir = Path.of(jsonDir);
    }

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        if (lawIds.isEmpty()) {
            throw new IllegalStateException("수집 대상 법령ID 가 없습니다. law.api.law-id-list 를 확인하세요.");
        }

        // 법률·시행령·시행규칙은 서로 참조하므로 일부만 갱신되면 정합성이 깨진다.
        // fetchLawJson 은 실패 시 예외를 던지므로, 한 건이라도 실패하면 아래 쓰기에
        // 도달하지 못하고 기존 파일은 그대로 남는다(전체 실패).
        Map<String, String> downloaded = new LinkedHashMap<>();
        for (String lawId : lawIds) {
            downloaded.put(lawId, lawApiClient.fetchLawJson(lawId));
        }

        // 전부 받은 뒤에야 쓴다. 파일명은 {법령ID}.json 고정이라 매주 최신으로 덮인다.
        Files.createDirectories(jsonDir);
        for (Map.Entry<String, String> entry : downloaded.entrySet()) {
            Files.writeString(jsonDir.resolve(entry.getKey() + ".json"),
                    entry.getValue(), StandardCharsets.UTF_8);
        }

        log.info("법령 JSON {}건 저장 완료: {}", downloaded.size(), jsonDir.toAbsolutePath());
        return RepeatStatus.FINISHED;
    }
}
