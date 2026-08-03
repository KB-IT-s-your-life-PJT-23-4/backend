package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder
@AllArgsConstructor
public record FamilyData(
        Long familyId,
        String name,
        String relationshipType,
        Long giftAmount,
        Integer recipientAge,
        Boolean recipientIsMinor,
        Boolean hasPreviousGifts,
        Long previousGiftAmount,
        LocalDate previousGiftDate,
        Boolean previousGiftSameDonor,
        Long previouslyUsedDeduction,
        LocalDate deductionRenewalDate
) {}