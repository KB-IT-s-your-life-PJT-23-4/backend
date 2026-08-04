package com.example.project.batch.law.chunk.reader;

import com.example.project.batch.law.chunk.LawUnit;

import com.example.project.batch.law.dto.raw.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;


// 파일을 조문단위 스트림으로 변환(json을 자바 객체로 역직렬화)
@Component
@Log4j2
public class LawArticleItemReader implements ItemReader<LawUnit> {

    private final Path jsonDir;
    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    private Iterator<LawUnit> iterator;

    public LawArticleItemReader(@Value("${law.json.dir}") String jsonDir) {
        this.jsonDir = Path.of(jsonDir);
    }

    @Override
    public LawUnit read() {
        if (iterator == null) {
            iterator = loadAll().iterator();
        }

        return iterator.hasNext() ? iterator.next() : null;
    }

    private List<LawUnit> loadAll() {
        List<Path> files = listJsonFiles();
        if (files.isEmpty()) {
            throw new IllegalStateException(
                    "읽을 법령 JSON 이 없습니다. Step 1 다운로드가 선행되어야 합니다. dir = " + jsonDir.toAbsolutePath());
        }

        List<LawUnit> units = new ArrayList<>();
        for (Path file : files) {
            units.addAll(toUnits(file));
        }

        log.info("법령 JSON {}개 파일 → LawUnit {}건", files.size(), units.size());
        return units;
    }

    private List<Path> listJsonFiles() {
        try (Stream<Path> stream = Files.list(jsonDir)) {
            return stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("법령 JSON 디렉터리를 읽을 수 없습니다. dir= " + jsonDir, e);
        }
    }

    private List<LawUnit> toUnits(Path file) {
        Law law;
        try {
            LawRoot root = objectMapper.readValue(file.toFile(), LawRoot.class);
            law = root.getLaw();
        } catch (IOException e) {
            throw new UncheckedIOException("법령 JSON 파싱 실패: " + file, e);
        }
        if (law == null || law.getBasicInfo() == null) {
            throw new IllegalStateException("법령 본문이 비어 있습니다: " + file);
        }

        String lawKey = law.getLawKey();
        List<LawUnit> units = new ArrayList<>();

        if (law.getArticles() != null && law.getArticles().getUnits() != null) {
            for (ArticleUnit u : law.getArticles().getUnits()) {
                units.add(LawUnit.ofArticle(law.getBasicInfo(), lawKey, u));
            }
        }

        if (law.getAppendices() != null && law.getAppendices().getUnits() != null) {
            for (AppendixUnit u : law.getAppendices().getUnits()) {
                units.add(LawUnit.ofAppendix(law.getBasicInfo(), lawKey, u));
            }
        }

        return units;
    }
}
