package com.example.project.recipient.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.recipient.domain.ENUM;
import com.example.project.recipient.domain.RecipientVO;
import com.example.project.recipient.dto.RecipientRequest;
import com.example.project.recipient.dto.RecipientResponse;
import com.example.project.recipient.mapper.RecipientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipientService {

    private final RecipientMapper recipientMapper;
    private final GiftMapper giftMapper;

    public RecipientResponse createRecipient(RecipientRequest recipientRequest, Long userId) {
        RecipientVO recipient = new RecipientVO();

        recipient.setUserId(userId);
        recipient.setFamilyName(requireFamilyName(recipientRequest.getFamilyName()));
        recipient.setRelation(requireRelation(recipientRequest.getRelation()));
        recipient.setBirthDate(requireBirthDate(recipientRequest.getBirthDate()));
        recipient.setFamilyImg(recipientRequest.getFamilyImg());

        recipientMapper.insertRecipient(recipient);

        return selectRecipient(recipient.getFamilyId(), userId);
    }

    public RecipientResponse updateRecipient(Long familyId, RecipientRequest recipientRequest, Long userId) {
        RecipientVO recipient = findOwnedRecipient(familyId, userId);

        // 조건은 본문 기준이어야 한다. 기존 값 기준으로 보면 NOT NULL 컬럼이 항상 참이라 전 필드가 필수가 된다.
        if (recipientRequest.getFamilyName() != null) {
            recipient.setFamilyName(requireFamilyName(recipientRequest.getFamilyName()));
        }

        if (recipientRequest.getRelation() != null) {
            recipient.setRelation(requireRelation(recipientRequest.getRelation()));
        }

        if (recipientRequest.getBirthDate() != null) {
            recipient.setBirthDate(requireBirthDate(recipientRequest.getBirthDate()));
        }

        if (recipientRequest.getFamilyImg() != null) {
            recipient.setFamilyImg(recipientRequest.getFamilyImg());
        }

        recipientMapper.updateRecipient(recipient);

        return selectRecipient(familyId, userId);
    }

    /**
     * gift FK 가 ON DELETE CASCADE 라 그냥 지우면 증여 이력과 리마인더가 함께 사라진다.
     * 이력이 있으면 기본적으로 막고, force 로 명시했을 때만 진행한다.
     */
    public void deleteRecipient(Long familyId, Long userId, boolean force) {
        findOwnedRecipient(familyId, userId);

        if (!force && giftMapper.countGiftByFamily(familyId) > 0) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        recipientMapper.deleteRecipient(familyId, userId);
    }

    public RecipientResponse selectRecipient(Long familyId, Long userId) {

        return RecipientResponse.from(findOwnedRecipient(familyId, userId));
    }

    public List<RecipientResponse> selectAllRecipient(Long userId) {
        return recipientMapper.selectAllRecipient(userId).stream()
                .map(RecipientResponse::from)
                .toList();
    }

    private RecipientVO findOwnedRecipient(Long familyId, Long userId) {
        RecipientVO recipient = recipientMapper.selectRecipient(familyId, userId);

        if (recipient == null) {
            throw new ServiceException(ResponseCode.BENEFICIARY_NOT_FOUND);
        }

        return recipient;
    }

    private String requireFamilyName(String familyName) {
        if (familyName == null || familyName.isBlank()) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        return familyName.trim();
    }

    private String requireRelation(String relation) {
        if (relation == null) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        try {
            return ENUM.valueOf(relation).name();
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }
    }

    private LocalDate requireBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        return birthDate;
    }
}
