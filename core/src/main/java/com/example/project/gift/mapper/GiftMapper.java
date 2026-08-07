package com.example.project.gift.mapper;

import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import com.example.project.gift.domain.TaxBracketVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface GiftMapper {

    int insertGift(GiftVO gift);

    /**
     * family 조인으로 소유권까지 검증한다. 남의 증여면 null.
     */
    GiftVO selectGift(@Param("giftId") Long giftId, @Param("userId") Long userId);

    List<GiftVO> selectAllGift(@Param("familyId") Long familyId,
                               @Param("status") Status status,
                               @Param("userId") Long userId);

    /**
     * null 인 필드는 건드리지 않는다. status 는 updateGiftStatus 담당.
     */
    int updateGift(@Param("giftId") Long giftId,
                   @Param("amount") Long amount,
                   @Param("giftDate") LocalDate giftDate,
                   @Param("memo") String memo);

    int updateGiftStatus(@Param("giftId") Long giftId, @Param("status") Status status);

    int deleteGift(@Param("giftId") Long giftId);

    /**
     * 수증자별 10년 합산 공제 현황. familyId 가 null 이면 해당 유저의 수증자 전원.
     * 증여 이력이 없는 수증자도 한도 전액이 남은 상태로 한 행 나온다.
     */
    List<DeductionVO> selectDeduction(@Param("familyId") Long familyId,
                                      @Param("userId") Long userId,
                                      @Param("windowStartDate") LocalDate windowStartDate,
                                      @Param("baseDate") LocalDate baseDate,
                                      @Param("excludeGiftId") Long excludeGiftId);

    /**
     * 합산 창 안의 확정 증여를 낱개로, 오래된 순으로. 한도 갱신일 계산용이다.
     * 집계만으로는 "가장 오래된 증여가 빠지는 날"까지밖에 못 구한다.
     */
    List<GiftVO> selectWindowGifts(@Param("familyId") Long familyId,
                                   @Param("userId") Long userId,
                                   @Param("windowStartDate") LocalDate windowStartDate,
                                   @Param("baseDate") LocalDate baseDate);

    /**
     * 특정 시점의 공제 한도 (미성년자는 다음 갱신일이 10년 한도 혹은 성년이 되는 날 중 빠른 날로 정해야함)
     * 해당 관계/미성년 행이 없으면 null
     *
     */
    Long selectDeductionLimit(@Param("relation") String relation,
                              @Param("minor") boolean minor,
                              @Param("baseDate") LocalDate baseDate);

    TaxBracketVO selectTaxBracket(@Param("baseDate") LocalDate baseDate,
                                  @Param("taxableBase") Long taxableBase);


    /**
     * 수증자 삭제 전 증여 이력 유무 확인용. gift FK 가 CASCADE 라 이 검사를 빼면 이력이 함께 사라진다.
     */
    int countGiftByFamily(@Param("familyId") Long familyId);
}
