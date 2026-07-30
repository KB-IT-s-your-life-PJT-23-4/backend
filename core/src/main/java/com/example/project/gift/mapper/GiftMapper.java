package com.example.project.gift.mapper;

import com.example.project.gift.domain.DeductionVO;
import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface GiftMapper {

    int insertGift(GiftVO gift);

    /** family 조인으로 소유권까지 검증한다. 남의 증여면 null. */
    GiftVO selectGift(@Param("giftId") Long giftId, @Param("userId") Long userId);

    List<GiftVO> selectAllGift(@Param("familyId") Long familyId,
                               @Param("status") Status status,
                               @Param("userId") Long userId);

    /** null 인 필드는 건드리지 않는다. status 는 updateGiftStatus 담당. */
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
                                      @Param("baseDate") LocalDate baseDate);

    /** 수증자 삭제 전 증여 이력 유무 확인용. gift FK 가 CASCADE 라 이 검사를 빼면 이력이 함께 사라진다. */
    int countGiftByFamily(@Param("familyId") Long familyId);
}
