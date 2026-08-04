package com.example.project.consultation.mapper;

import com.example.project.consultation.domain.FaqCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FaqCategoryMapper {

    List<FaqCategory> findAll();
}
