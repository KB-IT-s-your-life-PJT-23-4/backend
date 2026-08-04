package com.example.project.consultation.mapper;

import com.example.project.consultation.domain.Faq;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FaqMapper {

    List<Faq> findAll();

    Faq findById(@Param("faqId") Long faqId);
}
