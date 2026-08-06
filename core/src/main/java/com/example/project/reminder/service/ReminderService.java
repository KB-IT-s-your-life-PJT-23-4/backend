package com.example.project.reminder.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.gift.domain.FilingDeadline;
import com.example.project.gift.domain.Status;
import com.example.project.gift.dto.response.DeductionResponse;
import com.example.project.gift.dto.response.GiftResponse;
import com.example.project.gift.service.GiftService;
import com.example.project.reminder.domain.ReminderReadVO;
import com.example.project.reminder.domain.ReminderType;
import com.example.project.reminder.dto.request.ReminderReadRequest;
import com.example.project.reminder.dto.response.ReminderResponse;
import com.example.project.reminder.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알림함에 뿌릴 리마인더를 조회 시점에 계산한다.
 * 적재 테이블도 배치도 쓰지 않는 이유는 {@link com.example.project.reminder} 패키지 설명 참고.
 *
 * <p>예외적으로 읽음 여부만은 유도할 수 없어 {@code reminder} 테이블에 남긴다.
 * 그래서 이 테이블에는 "읽은 리마인더"만 행이 생긴다.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    /**
     * 신고기한 알림이 뜨는 시점(기한 며칠 전).
     *
     * <p>기한까지 매일 띄우면 같은 카드가 넉 달을 앉아 있어 알림이 아니라 벽지가 된다.
     * 정해진 시점에만 띄우고 사이에는 물러나 있는다.
     */
    private static final int[] FILING_MILESTONES = {90, 30, 7, 0};

    /**
     * 공제 갱신 알림 시점. 신고기한보다 성기다.
     * 갱신일은 마감이 아니라 여력이 생기는 날이라 90일 전부터 알릴 이유가 없고, 지나도 손해가 없다.
     */
    private static final int[] RENEWAL_MILESTONES = {30, 7, 0};

    /** 한 마일스톤이 알림함에 머무는 기간. 이후에는 다음 마일스톤까지 사라진다. */
    private static final int MILESTONE_VISIBLE_DAYS = 7;

    /**
     * 기한이 지난 신고 알림을 남겨 두는 기간. 놓친 신고는 계속 눈에 띄어야 해서 예외로 길게 잡는다.
     * 갱신일에는 적용하지 않는다.
     */
    private static final int OVERDUE_VISIBLE_DAYS = 30;

    private final GiftService giftService;
    private final ReminderMapper reminderMapper;

    /** 신고기한·공제 갱신일 리마인더를 합쳐 가까운 날짜순으로 돌려준다. */
    public List<ReminderResponse> selectReminders(Long userId) {
        LocalDate today = LocalDate.now();

        // 갱신일과 그것을 끄는 증여 id 가 이 응답에 이미 들어 있고, 수증자 이름도 여기서 한 번에 얻는다.
        List<DeductionResponse> deductions = giftService.selectDeduction(null, userId);
        Map<String, LocalDateTime> reads = reads(userId);

        List<ReminderResponse> reminders = new ArrayList<>();
        reminders.addAll(renewalReminders(deductions, reads, today));
        reminders.addAll(filingReminders(userId, familyNames(deductions), reads, today));

        reminders.sort(Comparator
                .comparing(ReminderResponse::getTargetDate)
                .thenComparing(ReminderResponse::getType)
                .thenComparing(ReminderResponse::getGiftId));

        return reminders;
    }

    /**
     * 읽음 처리. 리마인더에는 고유 id 가 없어 목록이 내려준 (giftId, type) 으로 지목한다.
     *
     * <p>대상 리마인더가 실제로 노출 창 안에 있는지까지는 따지지 않는다. 읽음 기록이 조금 앞서 생겨도
     * 목록에 없으면 아무 데도 안 쓰이고, 창을 다시 벗어나면 그대로 남아 있어도 무해하다.
     */
    @Transactional
    public void markRead(Long userId, ReminderReadRequest request) {
        if (request == null || request.getGiftId() == null || request.getType() == null) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        // 남의 증여에 읽음 기록을 남기지 못하게 소유권부터 확인한다. 아니면 GIFT_HISTORY_NOT_FOUND.
        GiftResponse gift = giftService.selectGift(request.getGiftId(), userId);

        reminderMapper.upsertRead(
                gift.getGiftId(),
                request.getType(),
                targetDate(gift, request.getType(), userId),
                LocalDateTime.now()
        );
    }

    /**
     * 공제 한도가 되살아나는 날.
     *
     * <p>갱신일은 "가장 오래된 증여가 창을 벗어나는 날"이 아니라 실제로 여력이 생기는 날이고,
     * 그 계산은 {@link GiftService#selectDeduction} 이 이미 끝내 놓았다. 여기서 다시 세지 않는다.
     */
    private List<ReminderResponse> renewalReminders(List<DeductionResponse> deductions,
                                                    Map<String, LocalDateTime> reads,
                                                    LocalDate today) {
        List<ReminderResponse> reminders = new ArrayList<>();

        for (DeductionResponse deduction : deductions) {
            LocalDate targetDate = deduction.getNextRenewalDate();

            // 확정 증여가 없거나 한도 행이 없는 관계는 갱신할 것 자체가 없다.
            if (targetDate == null) {
                continue;
            }

            LocalDate notifyFrom = activeMilestone(today, targetDate, RENEWAL_MILESTONES, false);

            if (notifyFrom == null) {
                continue;
            }

            Long giftId = deduction.getRenewalGiftId();

            reminders.add(ReminderResponse.of(
                    ReminderType.DEDUCTION_RENEWAL,
                    giftId,
                    deduction.getFamilyId(),
                    deduction.getFamilyName(),
                    null,
                    null,
                    targetDate,
                    notifyFrom,
                    today,
                    readAtIn(reads, giftId, ReminderType.DEDUCTION_RENEWAL, notifyFrom)
            ));
        }

        return reminders;
    }

    /**
     * 증여세 신고기한. 계획(PLANNED)과 확정(COMPLETED)을 모두 본다. CANCELLED 는 무효라 뺀다.
     *
     * <p>계획까지 보는 이유는 확정을 늦게 누르면 알림이 아예 못 뜨기 때문이다.
     * 6월에 증여하고 10월에야 확정을 누르면 9월 말 신고기한이 PLANNED 인 채로 지나가 버린다.
     * 시뮬레이션에서 사용자가 직접 고른 증여 예정일이 {@code gift_date} 로 들어오고
     * 이 서비스는 <b>예정일을 이체일로 본다</b>. 그래서 확정 전후로 기한 값이 달라지지 않는다.
     *
     * <p>status 를 함께 내리는 것은 기한이 불확실해서가 아니라, 필수 서류 확인이 안 끝난 건에
     * 확정 유도 문구를 덧붙이기 위해서다.
     */
    private List<ReminderResponse> filingReminders(Long userId,
                                                   Map<Long, String> familyNames,
                                                   Map<String, LocalDateTime> reads,
                                                   LocalDate today) {
        List<ReminderResponse> reminders = new ArrayList<>();

        for (GiftResponse gift : giftService.selectAllGift(null, null, userId)) {
            if (gift.getStatus() == Status.CANCELLED) {
                continue;
            }

            LocalDate targetDate = FilingDeadline.of(gift.getGiftDate());
            LocalDate notifyFrom = activeMilestone(today, targetDate, FILING_MILESTONES, true);

            if (notifyFrom == null) {
                continue;
            }

            reminders.add(ReminderResponse.of(
                    ReminderType.FILING_DEADLINE,
                    gift.getGiftId(),
                    gift.getFamilyId(),
                    familyNames.get(gift.getFamilyId()),
                    gift.getAmount(),
                    gift.getStatus(),
                    targetDate,
                    notifyFrom,
                    today,
                    readAtIn(reads, gift.getGiftId(), ReminderType.FILING_DEADLINE, notifyFrom)
            ));
        }

        return reminders;
    }

    /** 읽음 기록을 (giftId, type) 로 찾을 수 있게 펼친다. */
    private Map<String, LocalDateTime> reads(Long userId) {
        return reminderMapper.selectReads(userId).stream()
                .collect(Collectors.toMap(
                        read -> readKey(read.getGiftId(), read.getReminderType()),
                        ReminderReadVO::getReadAt));
    }

    private String readKey(Long giftId, ReminderType type) {
        return giftId + ":" + type;
    }

    /**
     * 읽음 행에 채울 target_date. 컬럼이 NOT NULL 이라 값이 필요하다.
     * 갱신일은 증여 1건만으로는 못 구해서 공제 조회를 다시 탄다.
     */
    private LocalDate targetDate(GiftResponse gift, ReminderType type, Long userId) {
        if (type == ReminderType.FILING_DEADLINE) {
            return FilingDeadline.of(gift.getGiftDate());
        }

        return giftService.selectDeduction(gift.getFamilyId(), userId).stream()
                .findFirst()
                .map(DeductionResponse::getNextRenewalDate)
                .orElseThrow(() -> new ServiceException(ResponseCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 오늘 이 리마인더가 어느 마일스톤 구간에 있는지. 어디에도 안 걸리면 null 이고 목록에서 빠진다.
     *
     * <p>각 마일스톤은 도달일부터 {@value #MILESTONE_VISIBLE_DAYS}일간만 머문다.
     * 예) 기한 10/31, 마일스톤 D-30 → 10/1 ~ 10/7 노출, 10/8 ~ 10/23 은 조용, D-7 인 10/24 에 재등장.
     *
     * <p>반환값은 "이번 알림이 시작된 날"이라 읽음 판정 기준으로도 쓴다({@link #readAtIn}).
     *
     * @param keepOverdue 기한이 지난 뒤에도 남길지. 신고기한만 true
     */
    private LocalDate activeMilestone(LocalDate today, LocalDate targetDate, int[] milestones, boolean keepOverdue) {
        for (int daysBefore : milestones) {
            LocalDate start = targetDate.minusDays(daysBefore);

            if (!today.isBefore(start) && today.isBefore(start.plusDays(MILESTONE_VISIBLE_DAYS))) {
                return start;
            }
        }

        // 기한 경과분은 D-Day 마일스톤을 그대로 이어 간다. 새 알림으로 다시 띄우지는 않고 자리만 지킨다.
        if (keepOverdue && !today.isBefore(targetDate) && !today.isAfter(targetDate.plusDays(OVERDUE_VISIBLE_DAYS))) {
            return targetDate;
        }

        return null;
    }

    /**
     * 이번 마일스톤에서의 읽음 시각. 이전 마일스톤에서 읽은 기록은 쓰지 않는다.
     *
     * <p>D-30 에 읽었더라도 D-7 이 되면 다시 안 읽음으로 되살아나야 한다.
     * 그래야 정작 급한 시점에 뱃지가 뜬다. 읽음 행은 하나뿐이지만 마일스톤 시작일과 비교하면
     * 별도 컬럼 없이 단계별 읽음이 표현된다.
     */
    private LocalDateTime readAtIn(Map<String, LocalDateTime> reads,
                                   Long giftId,
                                   ReminderType type,
                                   LocalDate milestoneStart) {
        LocalDateTime readAt = reads.get(readKey(giftId, type));

        return readAt == null || readAt.toLocalDate().isBefore(milestoneStart) ? null : readAt;
    }

    /** 공제 조회는 증여 이력이 없는 수증자도 한 행씩 내려주므로 이름이 빠지는 수증자는 없다. */
    private Map<Long, String> familyNames(List<DeductionResponse> deductions) {
        return deductions.stream()
                .collect(Collectors.toMap(DeductionResponse::getFamilyId, DeductionResponse::getFamilyName));
    }

}
