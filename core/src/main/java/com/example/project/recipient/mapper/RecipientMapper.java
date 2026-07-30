package com.example.project.recipient.mapper;

import com.example.project.recipient.domain.RecipientVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecipientMapper {

    RecipientVO selectRecipient(@Param("familyId") Long familyId, @Param("userId") Long userId);

    List<RecipientVO> selectAllRecipient(@Param("userId") Long userId);

    void insertRecipient(RecipientVO recipient);

    void updateRecipient(RecipientVO recipient);

    void deleteRecipient(@Param("familyId") Long familyId, @Param("userId") Long userId);
}
