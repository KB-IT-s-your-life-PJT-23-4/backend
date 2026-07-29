package com.example.project.gift.mapper;

import com.example.project.gift.domain.GiftVO;
import com.example.project.gift.domain.Status;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GiftMapper {

    int insertGift(GiftVO gift);

    /** family 조인으로 소유권까지 검증한다. 남의 증여면 null. */
    GiftVO selectGift(@Param("giftId") Long giftId, @Param("userId") Long userId);

    List<GiftVO> selectAllGift(@Param("familyId") Long familyId,
                               @Param("status") Status status,
                               @Param("userId") Long userId);

    int updateGiftStatus(@Param("giftId") Long giftId, @Param("status") Status status);

    int deleteGift(@Param("giftId") Long giftId);

    /** 수증자 삭제 전 증여 이력 유무 확인용. gift FK 가 CASCADE 라 이 검사를 빼면 이력이 함께 사라진다. */
    int countGiftByFamily(@Param("familyId") Long familyId);
}
