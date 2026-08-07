package com.example.project.admin.faq.dto.response;

import com.example.project.common.api.Pagination;
import com.example.project.consultation.domain.Faq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class AdminFaqPageResponse {
    private List<AdminFaqPageResponse> faqs;
    private Pagination pagination;
}
