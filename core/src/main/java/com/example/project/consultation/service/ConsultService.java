package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.client.FastApiClient;
import com.example.project.consultation.domain.FamilyPreviousGiftVO;
import com.example.project.consultation.dto.fastapi.ChatRequest;
import com.example.project.consultation.dto.fastapi.ChatResponse;
import com.example.project.consultation.dto.fastapi.ClarificationRequest;
import com.example.project.consultation.dto.fastapi.FamilyData;
import com.example.project.consultation.dto.request.ConsultClarificationRequest;
import com.example.project.consultation.dto.response.ConsultResponse;
import com.example.project.consultation.mapper.ConsultationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsultService {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 500;
    private static final int MINOR_AGE_STANDARD = 19;
    private static final String PARENT_TO_MINOR_CHILD = "parent_to_minor_child";
    private static final String PARENT_TO_ADULT_CHILD = "parent_to_adult_child";

    private final FastApiClient fastApiClient;
    private final ConsultationMapper consultationMapper;

    // 최초 질문
    public ConsultResponse consult(String question, Long userId) {
        validateQuestion(question);

        ChatRequest request = new ChatRequest(
                null, // 최초 요청은 conversation_id가 null
                question,
                fetchFamilies(userId),      // TODO family 도메인 연동 필요, 현재 빈 리스트
                fetchProduct(userId),       // TODO product 도메인 연동 필요, 현재 null
                Map.of()                    // TODO 초기 facts 정책 확정 필요, 현재 빈 맵
        );

        ChatResponse response = fastApiClient.startChat(request);
        return ConsultResponse.from(response);
    }

    // 추가 답변 제출
    public ConsultResponse answerClarification(ConsultClarificationRequest req, Long userId) {
        if (req.answers() == null || req.answers().isEmpty()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        ClarificationRequest request = new ClarificationRequest(
                req.conversationId(),
                req.question(),
                req.intent(),
                req.requiresCalculation(),
                req.facts(),
                req.answers(),
                fetchFamilies(userId),      // TODO family 도메인 연동 필요
                fetchProduct(userId)        // TODO product 도메인 연동 필요
        );

        ChatResponse response = fastApiClient.submitClarification(request);
        return ConsultResponse.from(response);
    }

    private void validateQuestion(String question) {
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

    // TODO family 도메인 완성 후 실제 조회 로직으로 교체
    private List<FamilyData> fetchFamilies(Long userId) {
        if(userId == null){
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        List<FamilyPreviousGiftVO> families = consultationMapper.selectAllByUserId(userId);

        return families.stream()
                .map(this::toFamilyData)
                .toList();
    }

    private FamilyData toFamilyData(FamilyPreviousGiftVO family){
        Integer recipientAge = family.getRecipientAge();

        if(recipientAge == null || recipientAge < 0){
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        boolean recipientIsMinor = recipientAge < MINOR_AGE_STANDARD;

        String relationshipType = recipientIsMinor ? PARENT_TO_MINOR_CHILD : PARENT_TO_ADULT_CHILD;

        boolean hasPreviousGifts = family.isHasPreviousGifts();

        Long previousGiftAmount = hasPreviousGifts ? family.getPreviousGiftAmount() : null;

        LocalDate previousGiftDate = hasPreviousGifts ? family.getPreviousGiftDate() : null;

        LocalDate deductionRenewDate = hasPreviousGifts ? family.getDeductionRenewalDate() : null;

        Boolean previousGiftSameDonor = hasPreviousGifts ? Boolean.TRUE : null;

        Long previouslyUsedDeduction = hasPreviousGifts ? requiredPreviousGiftAmount(previousGiftAmount) : 0L;

        return FamilyData.builder()
                .familyId(family.getFamilyId())
                .name(family.getName())
                .relationshipType(relationshipType)
                .giftAmount(null)
                .recipientAge(recipientAge)
                .recipientIsMinor(recipientIsMinor)
                .hasPreviousGifts(hasPreviousGifts)
                .previousGiftAmount(previousGiftAmount)
                .previousGiftDate(previousGiftDate)
                .previousGiftSameDonor(previousGiftSameDonor)
                .previouslyUsedDeduction(previouslyUsedDeduction)
                .deductionRenewalDate(deductionRenewDate)
                .build();
    }

    private Long requiredPreviousGiftAmount(Long previousGiftAmount){
        if (previousGiftAmount == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return previousGiftAmount;
    }
    // TODO product 도메인 완성 후 실제 조회 로직으로 교체
    private Object fetchProduct(Long userId) {
        return null;
    }
}