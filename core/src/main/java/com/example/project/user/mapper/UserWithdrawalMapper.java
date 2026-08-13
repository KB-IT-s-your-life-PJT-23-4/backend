package com.example.project.user.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserWithdrawalMapper {

    List<String> findFamilyImagePaths(Long userId);

    int deleteAiSafetyReportsByTriggerEventUserId(Long userId);

    int deleteAiSafetyReportsByUserId(Long userId);

    int deleteAiConsultationEventsByUserId(Long userId);

    int deleteAiConversationsByUserId(Long userId);

    int deleteTicketsByUserId(Long userId);
}
