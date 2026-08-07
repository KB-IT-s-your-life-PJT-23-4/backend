package com.example.project.admin.faq.mapper;

import com.example.project.admin.faq.dto.response.AdminFaqItemResponse;
import com.example.project.consultation.domain.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.PathVariable;

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

    Faq findFaqDetail(@Param("faqId") Long faqId);
}
