package com.example.project.batch.law.chunk.writer;

import com.example.project.batch.law.dto.LawArticle;
import com.example.project.batch.law.mapper.LawArticleMapper;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LawArticleItemWriter implements ItemWriter<LawArticle> {

    private final LawArticleMapper lawArticleMapper;

    public LawArticleItemWriter(LawArticleMapper lawArticleMapper) {
        this.lawArticleMapper = lawArticleMapper;
    }

    /**
     * Processor 가 걸러낸(null) 항목만 담긴 청크가 생기면 빈 리스트로 호출된다.
     * 서식·편장절 헤더가 연달아 나오면 실제로 그렇게 된다. 그대로 넘기면 INSERT 의
     * VALUES 뒤가 비어 문법 오류가 나므로 여기서 끊는다.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void write(List<? extends LawArticle> items) throws Exception {
        if (items.isEmpty()) {
            return;
        }

        lawArticleMapper.insertBatch((List<LawArticle>) items);
    }
}
