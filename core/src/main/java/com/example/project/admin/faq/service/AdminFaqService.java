package com.example.project.admin.faq.service;

import com.example.project.admin.faq.dto.response.AdminFaqCategoriesDto;
import com.example.project.admin.faq.dto.response.AdminFaqItemResponse;
import com.example.project.admin.faq.dto.response.AdminFaqPageResponse;
import com.example.project.admin.faq.mapper.AdminFaqMapper;
import com.example.project.common.api.Pagination;
import com.example.project.consultation.domain.FaqCategory;
import com.example.project.consultation.mapper.FaqCategoryMapper;
import com.example.project.consultation.mapper.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminFaqService {

    private final AdminFaqMapper adminFaqMapper;
    private final FaqCategoryMapper faqCategoryMapper;

    public AdminFaqPageResponse getFaqPage(
            int page,
            int size,
            Long categoryId,
            String keyword
    ){

        long offset = Pagination.calculateOffset(page, size);

        long totalFaqCount = adminFaqMapper.countFaqs(
                categoryId,
                keyword
        );

        List<AdminFaqItemResponse> items = adminFaqMapper.selectFaqPage(categoryId, keyword, offset, size);

        Pagination pagination = Pagination.of(
                page,
                size,
                totalFaqCount,
                items.size()
        );

        return AdminFaqPageResponse.builder()
                .faqs(items)
                .pagination(pagination)
                .build();
    }

    public AdminFaqCategoriesDto getCategories(){

        List<FaqCategory> categories = faqCategoryMapper.findAll();

        return AdminFaqCategoriesDto.builder()
                .categories(categories)
                .build();
    }

}
