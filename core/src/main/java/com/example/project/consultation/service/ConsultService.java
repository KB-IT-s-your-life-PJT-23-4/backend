package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.springframework.stereotype.Service;

@Service
public class ConsultService {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 500; // nnn 확정 필요

    public void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String trimmed = question.replaceAll("\\s", "");

        if (trimmed.length() < MIN_LENGTH) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        if (question.length() > MAX_LENGTH) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }
    }
}