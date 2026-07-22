package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.domain.Faq;
import com.example.project.consultation.dto.response.FaqAnswerResponse;
import com.example.project.consultation.dto.response.FaqListResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FaqService {

    private static final List<Faq> FAQ_LIST = List.of(
            new Faq(1L, "현금 증여 공제",
                    "직계존속으로부터 증여받는 경우 10년간 5천만원(미성년자는 2천만원)까지 공제됩니다.", 1),
            new Faq(2L, "주식 증여 신고",
                    "주식 증여 시 증여일 전후 2개월씩 총 4개월간의 종가 평균으로 평가하여 신고합니다.", 2),
            new Faq(3L, "주식 매매 세금",
                    "상장주식 매매 시 대주주가 아닌 개인은 통상 양도소득세가 과세되지 않으며, 증권거래세만 부담합니다.", 3)
    );

    public FaqListResponse getFaqList() {
        return FaqListResponse.from(FAQ_LIST);
    }

    public FaqAnswerResponse getFaqAnswer(Long faqId) {
        Faq faq = FAQ_LIST.stream()
                .filter(f -> f.getId().equals(faqId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ResponseCode.RESOURCE_NOT_FOUND));

        return FaqAnswerResponse.from(faq);
    }
}