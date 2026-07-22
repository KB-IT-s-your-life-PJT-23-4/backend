package com.example.project.batch.law.writer;

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

    @SuppressWarnings("unchecked")
    @Override
    public void write(List<? extends LawArticle> items) throws Exception {
        lawArticleMapper.insertBatch((List<LawArticle>) items);
    }
}
