package com.example.project.admin.faq.mapper;

import com.example.project.admin.faq.dto.response.AdminFaqItemResponse;
import com.example.project.consultation.domain.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminFaqMapper {

    List<AdminFaqItemResponse> selectFaqPage(
        @Param("categoryId") Long categoryId,
        @Param("keyword") String keyword,
        @Param("offset") long offset,
        @Param("size") int size
    );

    long countFaqs(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword
    );

    long totalFaqCount(@Param("categoryId") Long categoryId);

    Faq findFaqDetail(@Param("faqId") Long faqId);
}
