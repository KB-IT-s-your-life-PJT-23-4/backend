package com.example.project.admin.faq.mapper;

import com.example.project.admin.faq.dto.response.AdminFaqItemResponse;
import com.example.project.consultation.domain.Faq;
import com.example.project.consultation.domain.FaqCategory;
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

    int createCategory(FaqCategory faqCategory);

    int updateCategory(FaqCategory faqCategory);

    int deleteByCategoryId(@Param("categoryId") long categoryId);

    int insertFaq(Faq faq);

    int updateFaq(Faq faq);

    int deleteByFaqId(@Param("faqId") long faqId);
}
