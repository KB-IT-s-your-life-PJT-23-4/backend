package com.example.project.admin.faq.service;

import com.example.project.admin.faq.dto.request.AdminCategoryRequest;
import com.example.project.admin.faq.dto.request.AdminFaqRequest;
import com.example.project.admin.faq.dto.response.*;
import com.example.project.admin.faq.mapper.AdminFaqMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.Faq;
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

    @Transactional
    public AdminFaqResponse createFaq(AdminFaqRequest request) {
        validateFaqRequest(request);

        Faq faq = toFaq(null, request);

        int insertedRows = adminFaqMapper.insertFaq(faq);
        validateAffectedRows(insertedRows);
        if (faq.getId() == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return toResponse(faq);
    }

    @Transactional
    public AdminFaqResponse updateFaq(long faqId, AdminFaqRequest request) {
        if (faqId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        validateFaqRequest(request);

        Faq faq = toFaq(faqId, request);
        int updatedRows = adminFaqMapper.updateFaq(faq);
        validateAffectedRows(updatedRows);

        return toResponse(faq);
    }

    @Transactional
    public void deleteFaq(long faqId) {
        if (faqId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        int deletedRows = adminFaqMapper.deleteByFaqId(faqId);
        validateAffectedRows(deletedRows);
    }

    private void validateFaqRequest(AdminFaqRequest request) {
        if (request == null
                || request.getCategoryId() == null
                || request.getCategoryId() <= 0
                || request.getQuestion() == null
                || request.getQuestion().isBlank()
                || request.getPrompt() == null
                || request.getPrompt().isBlank()
                || request.getAnswer() == null
                || request.getAnswer().isBlank()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
    }

    private Faq toFaq(Long faqId, AdminFaqRequest request){

        return Faq.builder()
                .id(faqId)
                .categoryId(request.getCategoryId())
                .question(request.getQuestion().trim())
                .prompt(request.getPrompt().trim())
                .answer(request.getAnswer().trim())
                .showBranchButton(request.isShowBranchButton())
                .showTaxOfficeButton(request.isShowTaxOfficeButton())
                .build();
    }

    private AdminFaqResponse toResponse(Faq faq) {
        return AdminFaqResponse.builder()
                .faqId(faq.getId())
                .categoryId(faq.getCategoryId())
                .question(faq.getQuestion())
                .prompt(faq.getPrompt())
                .answer(faq.getAnswer())
                .showBranchButton(faq.isShowBranchButton())
                .showTaxOfficeButton(faq.isShowTaxOfficeButton())
                .build();
    }
}
