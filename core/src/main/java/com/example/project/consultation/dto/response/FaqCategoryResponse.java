package com.example.project.consultation.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class FaqCategoryResponse {

    private final String title;
    private final List<FaqItemResponse> items;
}