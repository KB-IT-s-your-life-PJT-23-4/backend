package com.example.project.batch.law.chunk.writer;

import com.example.project.batch.law.dto.LawArticle;
import com.example.project.batch.law.dto.LawArticleDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.file.transform.LineAggregator;

/*
 * LawArticle 하나를 jsonl 한 줄로 바꾼다.
 * <p>직렬화가 실패하면 예외를 던져 청크를 롤백시킨다. 한 줄만 건너뛰고 넘어가면 DB 에는 있고
 * jsonl 에는 없는 조문이 생기는데, 임베딩에서 빠진 것을 알아챌 방법이 없어 조용히 검색 누락이 된다.
 */
public class LawArticleJsonlAggregator implements LineAggregator<LawArticle> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String aggregate(LawArticle article) {
        try {
            return objectMapper.writeValueAsString(LawArticleDocument.from(article));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "jsonl 직렬화 실패: " + article.getLawName() + " " + article.getArticleNo(), e);
        }
    }
}
