package com.example.project.gift.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.example.project.gift.dto.request.GiftRequest;
import com.example.project.gift.dto.response.DeductionResponse;
import com.example.project.gift.dto.response.GiftResponse;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.recipient.service.RecipientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GiftService {

    private static final int DEDUCTION_WINDOW_YEARS = 10;

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

    /** status 와 familyId 는 여기서 바꾸지 않는다. 상태 전이는 updateGiftStatus 한 곳에서만 검증한다. */
    public GiftResponse updateGift(Long giftId, GiftRequest giftRequest, Long userId) {
        findOwnerGift(giftId, userId);

        if (giftRequest.getAmount() != null && giftRequest.getAmount() <= 0) {
            throw new ServiceException(ResponseCode.INVALID_GIFT_AMOUNT);
        }

        giftMapper.updateGift(giftId, giftRequest.getAmount(), giftRequest.getGiftDate(), giftRequest.getMemo());

        return selectGift(giftId, userId);
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

    /**
     * 남은 증여재산공제 조회. 합산 창은 조회 시점(baseDate)에서 소급 10년이다.
     * "지금 증여하면 얼마가 남았나"를 보는 화면이라 각 증여일 소급이 아니라 오늘을 기준으로 잡는다.
     */
    public List<DeductionResponse> selectDeduction(Long familyId, Long userId) {
        LocalDate baseDate = LocalDate.now();
        LocalDate windowStartDate = baseDate.minusYears(DEDUCTION_WINDOW_YEARS);

        if (familyId != null) {
            recipientService.selectRecipient(familyId, userId);
        }

        return giftMapper.selectDeduction(familyId, userId, windowStartDate, baseDate).stream()
                .map(deduction -> DeductionResponse.from(
                        deduction, windowStartDate, baseDate, nextRenewalDate(deduction)))
                .toList();
    }

    public void deleteGift(Long giftId, Long userId) {
        GiftVO gift = findOwnerGift(giftId, userId);

        if (!gift.getStatus().deletable()) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        giftMapper.deleteGift(giftId);
    }

    /**
     * 한도 갱신일 = 창 안에서 가장 오래된 확정 증여일 + 10년.
     * 그 증여가 창을 벗어나면서 쓴 만큼의 한도가 되살아난다. 확정 증여가 없으면 갱신할 것도 없다.
     */
    private LocalDate nextRenewalDate(DeductionVO deduction) {
        LocalDate oldest = deduction.getOldestGiftDate();

        return oldest == null ? null : oldest.plusYears(DEDUCTION_WINDOW_YEARS);
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
