package com.example.project.consultation.mapper;


import com.example.project.consultation.domain.EtfVO;
import com.example.project.consultation.domain.FamilyPreviousGiftVO;
import com.example.project.consultation.domain.ProductVO;
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
    List<ProductVO> selectAllOnSaleProducts();
    List<EtfVO> selectAllOnSaleEtfProducts();
}
