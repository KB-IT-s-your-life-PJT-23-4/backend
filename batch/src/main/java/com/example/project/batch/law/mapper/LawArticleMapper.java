package com.example.project.batch.law.mapper;

import com.example.project.batch.law.dto.LawArticle;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LawArticleMapper {

    void insertBatch(List<LawArticle> articles);
}
