package com.example.project.gift.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.example.project.gift.dto.GiftRequest;
import com.example.project.gift.dto.GiftResponse;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.recipient.service.RecipientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.project.gift.dto.GiftResponse.from;

@Service
@RequiredArgsConstructor
public class GiftService {

    private final GiftMapper giftMapper;
    private final RecipientService recipientService;

    public GiftResponse createGift(GiftRequest giftRequest, Long userId) {
        validate(giftRequest);
        recipientService.selectRecipient(giftRequest.getFamilyId(), userId);

        GiftVO gift = new GiftVO();
        gift.setFamilyId(giftRequest.getFamilyId());
        gift.setAmount((giftRequest.getAmount()));
        gift.setGiftDate(giftRequest.getGiftDate());
        gift.setStatus(giftRequest.getStatus() == null ? Status.PLANNED : giftRequest.getStatus());
        gift.setMemo(giftRequest.getMemo());

        giftMapper.insertGift(gift);

        return selectGift(gift.getGiftId(), userId);
    }

    public List<GiftResponse> selectAllGift(Long familyId, Status status, Long userId) {
        return giftMapper.selectAllGift(familyId, status, userId).stream().map(GiftResponse::from).toList();
    }

    public GiftResponse selectGift(Long giftId, Long userId) {
        return GiftResponse.from(findOwnerGift(giftId, userId));
    }

    public GiftResponse updateGiftStatus(Long giftId, Status status, Long userId) {
        if (status == null) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        GiftVO gift = findOwnerGift(giftId, userId);

        if (!gift.getStatus().canTransitionTo(status)) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        giftMapper.updateGiftStatus(giftId, status);

        return selectGift(giftId, userId);
    }

    public void deleteGift(Long giftId, Long userId) {
        GiftVO gift = findOwnerGift(giftId, userId);

        if (!gift.getStatus().deletable()) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        giftMapper.deleteGift(giftId);
    }

    private void validate(GiftRequest giftRequest) {
        if (giftRequest.getFamilyId() == null || giftRequest.getGiftDate() == null) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        if (giftRequest.getAmount() == null || giftRequest.getAmount() <= 0) {
            throw new ServiceException(ResponseCode.INVALID_GIFT_AMOUNT);
        }
    }

    private GiftVO findOwnerGift(Long giftId, Long userId) {
        GiftVO gift = giftMapper.selectGift(giftId, userId);

        if (gift == null) {
            throw new ServiceException(ResponseCode.GIFT_HISTORY_NOT_FOUND);
        }

        return gift;
    }
}
