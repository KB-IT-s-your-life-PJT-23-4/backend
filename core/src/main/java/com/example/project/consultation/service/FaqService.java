package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.Faq;
import com.example.project.consultation.domain.FaqCategory;
import com.example.project.consultation.dto.response.FaqAnswerResponse;
import com.example.project.consultation.dto.response.FaqCategoryResponse;
import com.example.project.consultation.dto.response.FaqItemResponse;
import com.example.project.consultation.dto.response.FaqListResponse;
import com.example.project.consultation.mapper.FaqCategoryMapper;
import com.example.project.consultation.mapper.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqCategoryMapper faqCategoryMapper;
    private final FaqMapper faqMapper;

    public FaqListResponse getFaqList() {
        List<FaqCategory> categories = faqCategoryMapper.findAll();
        List<Faq> faqs = faqMapper.findAll();

        Map<Long, List<Faq>> faqsByCategoryId = faqs.stream()
                .collect(Collectors.groupingBy(Faq::getCategoryId, LinkedHashMap::new, Collectors.toList()));

        List<FaqCategoryResponse> categoryResponses = categories.stream()
                .map(category -> new FaqCategoryResponse(
                        category.getCategoryName(),
                        faqsByCategoryId.getOrDefault(category.getId(), List.of()).stream()
                                .map(FaqItemResponse::from)
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        return FaqListResponse.of(categoryResponses);
    }

    public FaqAnswerResponse getFaqAnswer(Long faqId) {
        Faq faq = faqMapper.findById(faqId);
        if (faq == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }
        return FaqAnswerResponse.from(faq);
    }
}