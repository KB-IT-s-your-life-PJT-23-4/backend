package com.example.project.admin.faq.service;

import com.example.project.admin.faq.dto.request.AdminCategoryRequest;
import com.example.project.admin.faq.dto.response.AdminCategoryResponse;
import com.example.project.admin.faq.dto.response.AdminFaqCategoriesDto;
import com.example.project.admin.faq.dto.response.AdminFaqItemResponse;
import com.example.project.admin.faq.dto.response.AdminFaqPageResponse;
import com.example.project.admin.faq.mapper.AdminFaqMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.FaqCategory;
import com.example.project.consultation.mapper.FaqCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public AdminCategoryResponse createCategory(AdminCategoryRequest request){
        long categoryId = adminFaqMapper.createCategory(request.getCategoryName());

        return AdminCategoryResponse.builder()
                .categoryId(categoryId)
                .categoryName(request.getCategoryName())
                .build();
    }

    @Transactional
    public AdminCategoryResponse updateCategory(AdminCategoryRequest request){
        if (request == null
                || request.getCategoryId() == null
                || request.getCategoryId() <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String categoryName = normalizeCategoryName(request.getCategoryName());

        int updatedRows = adminFaqMapper.updateCategory(FaqCategory.builder()
                .id(request.getCategoryId())
                .categoryName(categoryName)
                .build());

        validateAffectedRows(updatedRows);

        return AdminCategoryResponse.builder()
                .categoryId(request.getCategoryId())
                .categoryName(categoryName)
                .build();
    }

    @Transactional
    public void deleteCategory(long categoryId){
        if (categoryId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        int deletedRows = adminFaqMapper.deleteByCategoryId(categoryId);
        validateAffectedRows(deletedRows);
    }

    private String normalizeCategoryName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        return categoryName.trim();
    }

    private void validateAffectedRows(int affectedRows) {
        if (affectedRows == 0) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        if (affectedRows != 1) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }
    }

}
