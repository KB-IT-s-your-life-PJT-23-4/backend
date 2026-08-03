package com.example.project.consultation.mapper;


import com.example.project.consultation.domain.FamilyPreviousGiftVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConsultationMapper {

    List<FamilyPreviousGiftVO> selectAllByUserId(
            @Param("userId") Long userId
    );

    FamilyPreviousGiftVO selectByFamilyId(
            @Param("familyId") Long familyId,
            @Param("userId") Long userId
    );
}
