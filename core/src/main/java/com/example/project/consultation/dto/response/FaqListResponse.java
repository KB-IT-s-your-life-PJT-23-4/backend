package com.example.project.consultation.dto.response;

import com.example.project.consultation.domain.Faq;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor(staticName = "of")
public class FaqListResponse {

    private final List<FaqCategoryResponse> categories;
}