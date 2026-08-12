package com.example.project.admin.faq.service;

import com.example.project.admin.audit.service.AdminAuditWriter;
import com.example.project.admin.auth.domain.AdminPrincipal;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminFaqService {

    private final AdminFaqMapper adminFaqMapper;
    private final FaqCategoryMapper faqCategoryMapper;
    private final AdminAuditWriter adminAuditWriter;

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
    public AdminCategoryResponse createCategory(
            AdminCategoryRequest request,
            AdminPrincipal actor
    ){
        if (request == null) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String categoryName = normalizeCategoryName(request.getCategoryName());
        FaqCategory category = FaqCategory.builder()
                .categoryName(categoryName)
                .build();

        int insertedRows = adminFaqMapper.createCategory(category);
        validateAffectedRows(insertedRows);
        if (category.getId() == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        adminAuditWriter.record(
                actor,
                "FAQ_CATEGORY_CREATE",
                "FAQ_CATEGORY",
                category.getId(),
                "FAQ 카테고리를 생성했습니다.",
                Map.of("categoryName", categoryName)
        );

        return AdminCategoryResponse.builder()
                .categoryId(category.getId())
                .categoryName(categoryName)
                .build();
    }

    @Transactional
    public AdminCategoryResponse updateCategory(
            AdminCategoryRequest request,
            AdminPrincipal actor
    ){
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

        adminAuditWriter.record(
                actor,
                "FAQ_CATEGORY_UPDATE",
                "FAQ_CATEGORY",
                request.getCategoryId(),
                "FAQ 카테고리를 수정했습니다.",
                Map.of("categoryName", categoryName)
        );

        return AdminCategoryResponse.builder()
                .categoryId(request.getCategoryId())
                .categoryName(categoryName)
                .build();
    }

    @Transactional
    public void deleteCategory(long categoryId, AdminPrincipal actor){
        if (categoryId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        int deletedRows = adminFaqMapper.deleteByCategoryId(categoryId);
        validateAffectedRows(deletedRows);

        adminAuditWriter.record(
                actor,
                "FAQ_CATEGORY_DELETE",
                "FAQ_CATEGORY",
                categoryId,
                "FAQ 카테고리를 삭제했습니다.",
                null
        );
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
    public AdminFaqResponse createFaq(
            AdminFaqRequest request,
            AdminPrincipal actor
    ) {
        validateFaqRequest(request);

        Faq faq = toFaq(null, request);

        int insertedRows = adminFaqMapper.insertFaq(faq);
        validateAffectedRows(insertedRows);
        if (faq.getId() == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        adminAuditWriter.record(
                actor,
                "FAQ_CREATE",
                "FAQ",
                faq.getId(),
                "FAQ를 생성했습니다.",
                Map.of(
                        "categoryId", faq.getCategoryId(),
                        "showBranchButton", faq.isShowBranchButton(),
                        "showTaxOfficeButton", faq.isShowTaxOfficeButton()
                )
        );

        return toResponse(faq);
    }

    @Transactional
    public AdminFaqResponse updateFaq(
            long faqId,
            AdminFaqRequest request,
            AdminPrincipal actor
    ) {
        if (faqId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        validateFaqRequest(request);

        Faq faq = toFaq(faqId, request);
        int updatedRows = adminFaqMapper.updateFaq(faq);
        validateAffectedRows(updatedRows);

        adminAuditWriter.record(
                actor,
                "FAQ_UPDATE",
                "FAQ",
                faqId,
                "FAQ를 수정했습니다.",
                Map.of(
                        "categoryId", faq.getCategoryId(),
                        "showBranchButton", faq.isShowBranchButton(),
                        "showTaxOfficeButton", faq.isShowTaxOfficeButton()
                )
        );

        return toResponse(faq);
    }

    @Transactional
    public void deleteFaq(long faqId, AdminPrincipal actor) {
        if (faqId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        int deletedRows = adminFaqMapper.deleteByFaqId(faqId);
        validateAffectedRows(deletedRows);

        adminAuditWriter.record(
                actor,
                "FAQ_DELETE",
                "FAQ",
                faqId,
                "FAQ를 삭제했습니다.",
                null
        );
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
