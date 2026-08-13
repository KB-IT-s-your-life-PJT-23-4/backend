package com.example.project.consultation.mapper;


import com.example.project.consultation.domain.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    Long lockUser(@Param("userId") Long userId);

    AiConversationVO selectActiveConversationForUpdate(@Param("userId") Long userId);

    AiConversationVO selectConversationForUpdate(
            @Param("aiConversationId")
            Long aiConversationId,
            @Param("userId")
            Long userId
            );

    AiConversationVO selectActiveConversation(@Param("userId") Long userId);

    int insertConversation(AiConversationVO conversation);

    int markConversationProcessing(
            @Param("aiConversationId")
            Long aiConversationId,
            @Param("userId")
            Long userId,
            @Param("transcriptJson")
            String transcriptJson,
            @Param("processingStartedAt")
            LocalDateTime processingStartedAt
    );

    int completeConversationTurn(
            @Param("aiConversationId")
            Long aiConversationId,
            @Param("userId")
            Long userId,
            @Param("conversationId")
            String conversationId,
            @Param("transcriptJson")
            String transcriptJson,
            @Param("completedAt")
            LocalDateTime completedAt
    );

    int failConversationTurn(
            @Param("aiConversationId")
            Long aiConversationId,
            @Param("userId")
            Long userId,
            @Param("transcriptJson")
            String transcriptJson,
            @Param("failedAt")
            LocalDateTime failedAt
    );



    int insertAIConsultationEvent(AiConsultationEventVO aiConsultationEventVO);

    int insertAIReport(AiSafetyReportVO aiSafetyReportVO);
}
