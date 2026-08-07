package com.example.project.admin.faq.service;

import com.example.project.admin.faq.dto.response.AdminFaqPageResponse;
import com.example.project.admin.faq.mapper.AdminFaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminFaqService {

    private AdminFaqMapper adminFaqMapper;


    public AdminFaqPageResponse getFaqPage(
            int page,
            int size,
            long categoryId,
            String keyword
    ){

        long offset =
    }
}
