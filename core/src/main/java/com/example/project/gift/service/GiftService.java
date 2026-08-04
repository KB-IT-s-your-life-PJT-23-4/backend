package com.example.project.gift.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.example.project.gift.domain.TaxBracketVO;
import com.example.project.gift.dto.request.GiftRequest;
import com.example.project.gift.dto.response.DeductionResponse;
import com.example.project.gift.dto.response.FilingInfoResponse;
import com.example.project.gift.dto.response.GiftResponse;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.recipient.service.RecipientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GiftService {

    private static final int DEDUCTION_WINDOW_YEARS = 10;
    private static final int FILING_DUE_MONTH = 3;
    private static final BigDecimal FILING_CREDIT_RATE = new BigDecimal("0.03");

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

        // 갱신일은 창 안 증여를 낱개로 봐야 나온다. 수증자 수와 무관하게 한 번만 조회해 묶는다.
        Map<Long, List<GiftVO>> windowGiftsByFamily = giftMapper
                .selectWindowGifts(familyId, userId, windowStartDate, baseDate).stream()
                .collect(Collectors.groupingBy(GiftVO::getFamilyId));

        return giftMapper.selectDeduction(familyId, userId, windowStartDate, baseDate, null).stream()
                .map(deduction -> DeductionResponse.from(
                        deduction, windowStartDate, baseDate,
                        nextRenewalDate(deduction, windowGiftsByFamily)))
                .toList();
    }

    /**
     * 증여 1건의 신고 안내. 공제 현황과 달리 기준일이 오늘이 아니라 <b>증여일</b>이다.
     * 신고는 그 증여가 일어난 시점의 합산 이력과 세율로 판단하기 때문이다.
     */
    public FilingInfoResponse getFilingInfo(Long giftId, Long userId) {
        GiftVO gift = findOwnerGift(giftId, userId);

        if (gift.getStatus() == Status.CANCELLED) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        LocalDate baseDate = gift.getGiftDate();
        LocalDate windowStartDate = baseDate.minusYears(DEDUCTION_WINDOW_YEARS);

        // 신고 대상 증여 자신은 기공제된 과거 증여가 아니라 이번 과세 대상이라 합산에서 뺀다.
        DeductionVO deduction = giftMapper
                .selectDeduction(gift.getFamilyId(), userId, windowStartDate, baseDate, giftId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ServiceException(ResponseCode.BENEFICIARY_NOT_FOUND));

        long giftAmount = gift.getAmount();
        long priorGiftAmount = deduction.getUsedAmount() == null ? 0L : deduction.getUsedAmount();
        Long deductionLimit = deduction.getDeductionLimit();

        FilingInfoResponse filingInfo = new FilingInfoResponse();
        filingInfo.setGiftId(gift.getGiftId());
        filingInfo.setFamilyId(gift.getFamilyId());
        filingInfo.setFamilyName(deduction.getFamilyName());
        filingInfo.setGiftDate(baseDate);
        filingInfo.setStatus(gift.getStatus().name());
        filingInfo.setEstimated(gift.getStatus() == Status.PLANNED);
        filingInfo.setGiftAmount(giftAmount);
        filingInfo.setFilingDueDate(filingDueDate(baseDate));
        filingInfo.setWindowStartDate(windowStartDate);
        filingInfo.setBaseDate(baseDate);
        filingInfo.setPriorGiftAmount(priorGiftAmount);
        filingInfo.setDeductionLimit(deductionLimit);

        // 한도 행이 없는 관계는 세액을 산출할 근거가 없어 금액 항목을 채우지 않는다.
        if (deductionLimit == null) {
            return filingInfo;
        }

        long appliedDeduction = Math.min(Math.max(0L, deductionLimit - priorGiftAmount), giftAmount);
        long taxableBase = giftAmount - appliedDeduction;

        BigDecimal taxRate = BigDecimal.ZERO;
        long calculatedTax = 0L;

        // 과세표준 0 은 최저구간의 lower_bound 초과 조건에 걸려 행이 안 나온다. 조회 자체를 건너뛴다.
        if (taxableBase > 0) {
            TaxBracketVO bracket = giftMapper.selectTaxBracket(baseDate, taxableBase);

            if (bracket == null) {
                throw new ServiceException(ResponseCode.DATABASE_ERROR);
            }

            taxRate = bracket.getTaxRate();
            calculatedTax = Math.max(0L, taxRate.multiply(BigDecimal.valueOf(taxableBase)).longValue()
                    - bracket.getProgressiveDeduction());
        }

        long filingCredit = FILING_CREDIT_RATE.multiply(BigDecimal.valueOf(calculatedTax)).longValue();

        filingInfo.setAppliedDeduction(appliedDeduction);
        filingInfo.setTaxableBase(taxableBase);
        filingInfo.setTaxRate(taxRate);
        filingInfo.setCalculatedTax(calculatedTax);
        filingInfo.setFilingCredit(filingCredit);
        filingInfo.setPayableTax(calculatedTax - filingCredit);

        return filingInfo;
    }

    /**
     * 신고기한 = 증여일이 속하는 달의 말일부터 3개월(상증법 제68조).
     * 초일불산입이라 "말일 + 3개월"이 아니라 3개월 뒤 달의 말일이 된다.
     * 예) 4/10 증여 -> 7/31 (4/30 에 3개월을 더한 7/30 이 아니다)
     */
    private LocalDate filingDueDate(LocalDate giftDate) {
        return giftDate.plusMonths(FILING_DUE_MONTH).with(TemporalAdjusters.lastDayOfMonth());
    }

    public void deleteGift(Long giftId, Long userId) {
        GiftVO gift = findOwnerGift(giftId, userId);

        if (!gift.getStatus().deletable()) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        giftMapper.deleteGift(giftId);
    }

    /**
     * 한도 갱신일 = 공제 여력이 실제로 생기는 첫 날.
     *
     * <p>가장 오래된 증여가 창을 벗어나는 날(= 그 증여일 + 10년)을 그대로 쓰면 안 된다.
     * 합산액이 한도를 크게 넘긴 상태면 한 건이 빠져도 여전히 초과라 여력이 0 그대로다.
     * 예) 한도 5,000만 / 창 안 8,250만(3,000 + 1,500 + 3,000 + 750)
     * → 3,000만이 빠져도 5,250만으로 여전히 초과. 1,500만까지 빠져 3,750만이 되는 날이 갱신일이다.
     *
     * <p>그래서 오래된 순으로 하나씩 걷어내며 남은 합이 한도 밑으로 내려가는 첫 증여를 찾고,
     * 그 증여일 + 10년을 돌려준다. 이미 여력이 있는 경우에는 첫 증여에서 바로 조건이 성립해
     * "가장 오래된 증여가 빠지는 날"이 그대로 나온다.
     *
     * <p>확정 증여가 없거나 한도 행이 없는 관계면 갱신할 것도 없어 null.
     */
    private LocalDate nextRenewalDate(DeductionVO deduction, Map<Long, List<GiftVO>> windowGiftsByFamily) {
        Long deductionLimit = deduction.getDeductionLimit();
        List<GiftVO> windowGifts = windowGiftsByFamily.getOrDefault(deduction.getFamilyId(), List.of());

        if (deductionLimit == null || windowGifts.isEmpty()) {
            return null;
        }

        long remaining = windowGifts.stream().mapToLong(GiftVO::getAmount).sum();

        for (GiftVO gift : windowGifts) {
            remaining -= gift.getAmount();

            if (remaining < deductionLimit) {
                return gift.getGiftDate().plusYears(DEDUCTION_WINDOW_YEARS);
            }
        }

        // 전부 걷어내면 remaining 이 0 이라 한도가 0 이 아닌 한 위에서 반환된다.
        return null;
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
