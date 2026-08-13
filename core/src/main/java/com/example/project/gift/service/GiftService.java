package com.example.project.gift.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.FilingDeadline;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.SimulationGiftSource;
import com.example.project.gift.domain.Status;
import com.example.project.gift.domain.TaxBracketVO;
import com.example.project.gift.dto.request.GiftRequest;
import com.example.project.gift.dto.request.SimulationGiftRequest;
import com.example.project.gift.dto.response.DeductionResponse;
import com.example.project.gift.dto.response.FilingInfoResponse;
import com.example.project.gift.dto.response.GiftResponse;
import com.example.project.gift.mapper.GiftMapper;
import com.example.project.recipient.service.RecipientService;
import com.example.project.simulation.domain.SimulationTrancheRecord;
import com.example.project.user.crypto.UserPiiProtectionService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GiftService {

    private static final int DEDUCTION_WINDOW_YEARS = 10;

    /** 성년 기준 나이. 이 나이가 되는 날부터 공제 한도가 성년 기준으로 올라간다. */
    private static final int ADULT_AGE = 19;

    private static final BigDecimal FILING_CREDIT_RATE = new BigDecimal("0.03");

    private final GiftMapper giftMapper;
    private final RecipientService recipientService;
    private final UserPiiProtectionService piiProtectionService;

    public GiftResponse createGift(GiftRequest giftRequest, Long userId) {
        validate(giftRequest);
        recipientService.selectRecipient(giftRequest.getFamilyId(), userId);

        Status status = giftRequest.getStatus() == null ? Status.PLANNED : giftRequest.getStatus();
        validateCompletedGiftDate(status, giftRequest.getGiftDate());

        GiftVO gift = new GiftVO();
        gift.setFamilyId(giftRequest.getFamilyId());
        gift.setAmount((giftRequest.getAmount()));
        gift.setGiftDate(giftRequest.getGiftDate());
        gift.setStatus(status);
        gift.setMemo(giftRequest.getMemo());

        giftMapper.insertGift(gift);

        return selectGift(gift.getGiftId(), userId);
    }

    /**
     * 저장된 시뮬레이션을 진행 중인 증여로 등록한다. 분할 증여면 <b>회차 수만큼 gift 행이 생긴다.</b>
     *
     * <p>회차를 한 행으로 합치지 않는 이유는 회차마다 증여일이 다르기 때문이다. 증여일이 다르면
     * 신고기한도 10년 합산 창에서 빠지는 날도 회차별로 따로 움직인다. 합쳐 놓으면 그 계산이 전부
     * 어긋나고, {@code reminder} 도 {@code (gift_id, reminder_type)} 이 유일해서 회차별 신고기한
     * 알림을 만들 수 없다.
     *
     * <p>금액·증여일은 요청이 아니라 {@code simulation_tranche} 에서 읽는다. 화면이 보낸 값을 믿으면
     * 시뮬레이션이 계산한 회차와 실제 등록된 증여가 어긋날 수 있다.
     *
     * <p>중복 등록은 {@code gift(simul_result_id, sequence_no)} UNIQUE 제약이 최종적으로 막는다.
     * 여기서 미리 세어 보는 것은 사용자에게 이유를 알려주기 위한 것이고, 동시에 두 번 눌러 검사를
     * 통과하더라도 DB 가 걸러 낸다.
     */
    @Transactional
    public List<GiftResponse> registerFromSimulation(SimulationGiftRequest request, Long userId) {
        if (request.getSimulationId() == null) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        SimulationGiftSource source = giftMapper.selectSimulationGiftSource(request.getSimulationId(), userId);

        if (source == null) {
            throw new ServiceException(ResponseCode.SIMULATION_NOT_REGISTRABLE);
        }

        if (giftMapper.countGiftBySimulResultId(source.getSimulResultId()) > 0) {
            throw new ServiceException(ResponseCode.SIMULATION_ALREADY_REGISTERED);
        }

        List<SimulationTrancheRecord> tranches = giftMapper.selectTranchesByResultId(source.getSimulResultId());

        if (tranches.isEmpty()) {
            throw new ServiceException(ResponseCode.SIMULATION_NOT_REGISTRABLE);
        }

        List<Long> giftIds = new ArrayList<>();

        for (SimulationTrancheRecord tranche : tranches) {
            GiftVO gift = new GiftVO();
            gift.setFamilyId(source.getFamilyId());
            gift.setSimulResultId(source.getSimulResultId());
            gift.setSequenceNo(tranche.getSequenceNo());
            gift.setAmount(tranche.getGiftAmount());
            gift.setGiftDate(tranche.getGiftDate());
            gift.setStatus(Status.PLANNED);
            gift.setMemo(memoFor(request.getMemo(), tranche.getSequenceNo(), tranches.size()));

            giftMapper.insertGift(gift);
            giftIds.add(gift.getGiftId());
        }

        return giftIds.stream().map(giftId -> selectGift(giftId, userId)).toList();
    }

    /** 회차가 하나뿐이면 분할이 아니므로 회차 표기를 붙이지 않는다. */
    private String memoFor(String requestedMemo, int sequenceNo, int totalCount) {
        if (requestedMemo != null && !requestedMemo.isBlank()) {
            return requestedMemo;
        }

        return totalCount == 1 ? "진행 중인 증여" : sequenceNo + "/" + totalCount + "회차 증여";
    }

    public List<GiftResponse> selectAllGift(Long familyId, Status status, Long userId) {
        return giftMapper.selectAllGift(familyId, status, userId).stream().map(GiftResponse::from).toList();
    }

    public GiftResponse selectGift(Long giftId, Long userId) {
        return GiftResponse.from(findOwnerGift(giftId, userId));
    }

    /** status 와 familyId 는 여기서 바꾸지 않는다. 상태 전이는 updateGiftStatus 한 곳에서만 검증한다. */
    public GiftResponse updateGift(Long giftId, GiftRequest giftRequest, Long userId) {
        GiftVO gift = findOwnerGift(giftId, userId);

        if (giftRequest.getAmount() != null && giftRequest.getAmount() <= 0) {
            throw new ServiceException(ResponseCode.INVALID_GIFT_AMOUNT);
        }

        // 이미 확정된 건의 증여일을 미래로 옮기는 것도 막는다. 여기서 새는 경로가 생기면 등록만 막아도 소용없다.
        validateCompletedGiftDate(gift.getStatus(), giftRequest.getGiftDate());

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

        // 증여일이 아직 오지 않은 계획은 확정할 수 없다. 확정은 "이체가 끝났다"는 뜻이다.
        validateCompletedGiftDate(status, gift.getGiftDate());

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
                .map(deduction -> revealDeduction(deduction, baseDate))
                .map(deduction -> {
                    RenewalEvent renewal = renewalEvent(deduction, windowGiftsByFamily, baseDate);

                    return DeductionResponse.from(
                            deduction, windowStartDate, baseDate,
                            renewal == null ? null : renewal.getDate(),
                            renewal == null ? null : renewal.getGiftId(),
                            renewal == null ? null : renewal.getAmount());
                })
                .toList();
    }

    /**
     * 증여 1건의 신고 안내. 공제 현황과 달리 기준일이 오늘이 아니라 <b>증여일</b>이다.
     * 신고는 그 증여가 일어난 시점의 합산 이력과 세율로 판단하기 때문이다.
     * 신고를 위한 메서드
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
                .map(value -> revealDeduction(value, baseDate))
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
        filingInfo.setFilingDueDate(FilingDeadline.of(baseDate));
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
        long priorTaxableBase = Math.max(0L, priorGiftAmount - deductionLimit);
        long cumulativeTaxableBase = Math.addExact(priorTaxableBase, taxableBase);

        BigDecimal taxRate = BigDecimal.ZERO;
        long calculatedTax = 0L;

        // 과거분을 포함한 누적 과세표준으로 구간을 판정하고, 과거분 세액은 빼서 이번 증분세액만 산출한다.
        if (cumulativeTaxableBase > 0) {
            ProgressiveTax cumulativeTax = calculateProgressiveTax(baseDate, cumulativeTaxableBase);
            ProgressiveTax priorTax = calculateProgressiveTax(baseDate, priorTaxableBase);
            taxRate = cumulativeTax.taxRate();
            calculatedTax = Math.max(0L, cumulativeTax.amount() - priorTax.amount());
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

    private ProgressiveTax calculateProgressiveTax(LocalDate baseDate, long taxableBase) {
        if (taxableBase <= 0) {
            return new ProgressiveTax(BigDecimal.ZERO, 0L);
        }

        TaxBracketVO bracket = giftMapper.selectTaxBracket(baseDate, taxableBase);
        if (bracket == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        long amount = Math.max(
                0L,
                bracket.getTaxRate()
                        .multiply(BigDecimal.valueOf(taxableBase))
                        .longValue()
                        - bracket.getProgressiveDeduction()
        );
        return new ProgressiveTax(bracket.getTaxRate(), amount);
    }

    private record ProgressiveTax(BigDecimal taxRate, long amount) {
    }

    /**
     * 증여 삭제. 계획(PLANNED)이든 확정 이력(COMPLETED)이든 상태를 가리지 않는다.
     *
     * <p>확정 이력까지 지울 수 있는 이유는 <b>10년 합산 결과를 어디에도 적재하지 않기</b> 때문이다.
     * 누적 증여액·남은 한도·갱신일은 전부 {@link #selectDeduction} 이 조회할 때마다 창 안의
     * COMPLETED 행을 다시 합산해 만들고, 리마인더도 그 결과에서 파생된다. 그래서 행 하나만
     * 지우면 화면 값이 전부 따라오고, 여기서 따로 재계산하거나 되돌릴 파생 데이터가 없다.
     * 리마인더 읽음 기록은 {@code reminder.gift_id} 의 ON DELETE CASCADE 로 함께 사라진다.
     *
     * <p>실제로 일어났던 증여를 <b>취소</b>한 것이라면 삭제가 아니라 {@link Status#CANCELLED} 전이가 맞다.
     * 삭제는 잘못 등록한 건을 없던 일로 만드는 쪽이라 흔적이 남지 않는다.
     *
     * <p>수증자(family) 삭제를 이력이 있으면 막는 것과 어긋나 보이지만 상황이 다르다.
     * 그쪽은 사용자가 지우려는 대상이 수증자 하나인데 증여 여러 건이 CASCADE 로 딸려 나가는 경우고,
     * 여기는 사용자가 지목한 증여 한 건만 사라진다.
     */
    public void deleteGift(Long giftId, Long userId) {
        findOwnerGift(giftId, userId);
        giftMapper.deleteGift(giftId);
    }

    /** 한도 갱신 이벤트. 날짜만으로는 부족해서 늘어나는 여력과 알림을 매달 증여까지 함께 담는다. */
    @Getter
    @RequiredArgsConstructor
    private static class RenewalEvent {

        /** 공제 여력이 실제로 늘어나는 첫 날. */
        private final LocalDate date;

        /** 갱신 알림을 식별할 증여. 리마인더에 고유 id 가 없어 (gift_id, type) 으로 지목한다. */
        private final Long giftId;

        /** 그날 늘어나는 공제 여력. */
        private final Long amount;
    }

    /**
     * 한도 갱신일 = 공제 여력이 실제로 늘어나는 첫 날.
     *
     * <p>여력을 움직이는 사건은 두 가지고, 그중 <b>먼저 오는 쪽</b>이 갱신일이다.
     * <ol>
     *   <li>증여가 합산 창을 벗어난다 — 그만큼 합산액이 줄어든다</li>
     *   <li>미성년 수증자가 성년이 된다 — 한도 자체가 올라간다(2,000만 → 5,000만)</li>
     * </ol>
     *
     * <p>둘 중 하나만 보면 틀린다. 예) 만 11세에게 1,700만원을 증여한 경우, 증여가 빠지는 날만
     * 보면 증여일 + 10년이 나오지만 그전에 성년이 되면서 한도가 3,000만원 더 늘어난다.
     * 성년 전환이 더 이르고 늘어나는 폭도 크다.
     *
     * <p>반대로 "증여가 빠지는 날"도 그대로 쓰면 안 된다. 합산액이 한도를 크게 넘긴 상태면
     * 한 건이 빠져도 여전히 초과라 여력이 0 그대로다.
     * 예) 한도 5,000만 / 창 안 8,250만(3,000 + 1,500 + 3,000 + 750)
     * → 3,000만이 빠져도 5,250만으로 여전히 초과. 1,500만까지 빠져 3,750만이 되는 날이 갱신일이다.
     *
     * <p>그래서 날짜를 추측하지 않고 <b>여력이 변할 수 있는 날을 모두 후보로 놓고</b>
     * 이른 순으로 훑어 지금보다 여력이 커지는 첫 날을 찾는다. 여력은 계단 함수라
     * 그 사이 날짜는 볼 필요가 없다.
     *
     * <p>확정 증여가 없거나 한도 행이 없는 관계면 갱신할 것도 없어 null.
     */
    private RenewalEvent renewalEvent(DeductionVO deduction,
                                      Map<Long, List<GiftVO>> windowGiftsByFamily,
                                      LocalDate baseDate) {
        Long currentLimit = deduction.getDeductionLimit();
        List<GiftVO> windowGifts = windowGiftsByFamily.getOrDefault(deduction.getFamilyId(), List.of());

        if (currentLimit == null || windowGifts.isEmpty()) {
            return null;
        }

        LocalDate adultDate = adultDate(deduction);
        Long adultLimit = adultDate == null
                ? null
                : giftMapper.selectDeductionLimit(deduction.getRelation(), false, adultDate);

        // 성년 한도 행이 없으면 그날 한도가 얼마가 되는지 알 수 없어 후보에서 뺀다.
        if (adultLimit == null) {
            adultDate = null;
        }

        long capacityNow = capacityAt(baseDate, windowGifts, currentLimit);

        for (LocalDate candidate : renewalCandidates(windowGifts, adultDate)) {
            long limit = adultDate != null && !candidate.isBefore(adultDate) ? adultLimit : currentLimit;
            long capacity = capacityAt(candidate, windowGifts, limit);

            if (capacity > capacityNow) {
                return new RenewalEvent(candidate, anchorGiftId(windowGifts, candidate), capacity - capacityNow);
            }
        }

        return null;
    }

    /** 여력이 변할 수 있는 날들. 이른 순, 중복 제거. */
    private List<LocalDate> renewalCandidates(List<GiftVO> windowGifts, LocalDate adultDate) {
        List<LocalDate> candidates = new ArrayList<>();

        for (GiftVO gift : windowGifts) {
            candidates.add(windowExitDate(gift.getGiftDate()));
        }

        if (adultDate != null) {
            candidates.add(adultDate);
        }

        return candidates.stream().distinct().sorted().toList();
    }

    /**
     * 그 증여가 합산 창에서 빠지는 첫 날.
     *
     * <p>증여일 + 10년이 아니라 <b>그 다음 날</b>이다. 창 조건이
     * {@code gift_date >= baseDate - 10년} 이라 소급 10년이 되는 날 당일까지는 아직 합산에 들어간다.
     * 하루 차이지만 여기서 증여일 + 10년을 쓰면 갱신일이라고 알린 날에 숫자가 그대로인 일이 생긴다.
     */
    private LocalDate windowExitDate(LocalDate giftDate) {
        return giftDate.plusYears(DEDUCTION_WINDOW_YEARS).plusDays(1);
    }

    /**
     * 미성년 수증자가 성년이 되는 날. 그날부터 성년 한도가 적용된다.
     *
     * <p>이미 성년이거나 나이로 한도가 갈리지 않는 관계면 null.
     * {@code minor} 는 조회 쿼리가 직계비속 + 만 19세 미만일 때만 세우므로 관계는 다시 보지 않는다.
     */
    private LocalDate adultDate(DeductionVO deduction) {
        if (!deduction.isMinor() || deduction.getBirthDate() == null) {
            return null;
        }

        return deduction.getBirthDate().plusYears(ADULT_AGE);
    }

    /**
     * 그 날짜 기준 공제 여력.
     *
     * <p>창 조건은 {@code selectDeduction} 쿼리와 같게 소급 10년이 되는 날을 포함한다.
     * 한도를 넘긴 상태면 음수가 아니라 0 이다 — 초과분은 다음 증여의 공제로 이월되지 않는다.
     */
    private long capacityAt(LocalDate date, List<GiftVO> windowGifts, long limit) {
        LocalDate windowStart = date.minusYears(DEDUCTION_WINDOW_YEARS);
        long used = windowGifts.stream()
                .filter(gift -> !gift.getGiftDate().isBefore(windowStart))
                .mapToLong(GiftVO::getAmount)
                .sum();

        return Math.max(0L, limit - used);
    }

    /**
     * 갱신 알림을 매달 증여.
     *
     * <p>증여가 빠져서 생긴 갱신이면 그날 빠지는 증여를 쓴다. 성년 전환이면 매달 증여가 따로 없어
     * 창 안 가장 오래된 증여를 쓴다({@code windowGifts} 는 오래된 순). 리마인더는 이 값을
     * 읽음 기록의 키로만 쓰므로 수증자별로 하나만 정해지면 된다.
     */
    private Long anchorGiftId(List<GiftVO> windowGifts, LocalDate renewalDate) {
        LocalDate leavingGiftDate = renewalDate.minusYears(DEDUCTION_WINDOW_YEARS).minusDays(1);

        return windowGifts.stream()
                .filter(gift -> gift.getGiftDate().equals(leavingGiftDate))
                .map(GiftVO::getGiftId)
                .findFirst()
                .orElseGet(() -> windowGifts.get(0).getGiftId());
    }

    private DeductionVO revealDeduction(DeductionVO deduction, LocalDate baseDate) {
        if (deduction.getFamilyNameEncrypted() != null) {
            deduction.setFamilyName(
                    piiProtectionService.decryptFamilyName(deduction.getFamilyNameEncrypted())
            );
        }
        if (deduction.getBirthDateEncrypted() != null) {
            deduction.setBirthDate(
                    piiProtectionService.decryptFamilyBirthDate(deduction.getBirthDateEncrypted())
            );
        }
        boolean minor = "LINEAL_DESCENDANT".equals(deduction.getRelation())
                && Period.between(deduction.getBirthDate(), baseDate).getYears() < ADULT_AGE;
        deduction.setMinor(minor);
        deduction.setDeductionLimit(giftMapper.selectDeductionLimit(
                deduction.getRelation(), minor, baseDate
        ));
        return deduction;
    }

    /**
     * 확정 이력은 오늘보다 뒤 날짜일 수 없다.
     *
     * <p>합산 창 상한이 오늘이라({@code gift_date <= baseDate}) 미래 날짜 확정 건은 누적 증여액에
     * 잡히지 않는다. 그런데 증여 목록에는 그대로 나오므로, 막지 않으면 <b>이력에는 보이는데
     * 공제 계산에는 없는</b> 건이 생겨 사용자에겐 숫자가 틀린 것처럼 보인다.
     *
     * <p>미래에 할 증여는 {@link Status#PLANNED} 라는 상태가 따로 있으니 확정으로 넣을 이유가 없다.
     * 계획을 미리 확정 처리하는 것도 같은 이유로 막는다.
     * */
    private void validateCompletedGiftDate(Status status, LocalDate giftDate) {
        if (status == Status.COMPLETED && giftDate != null && giftDate.isAfter(LocalDate.now())) {
            throw new ServiceException(ResponseCode.INVALID_GIFT_DATE);
        }
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
