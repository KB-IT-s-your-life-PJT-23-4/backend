package com.example.project.consultation.dto.fastapi;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Getter
@Builder
@AllArgsConstructor
public class FamilyData {

    private final Long familyId;
    private final String name;
    private final String relationshipType;
    private final Long giftAmount;
    private final Boolean recipientIsMinor;
    private final Boolean hasPreviousGifts;
    private final Long previousGiftAmount;
    private final LocalDate previousGiftDate;
    private final Boolean previousGiftSameDonor;
    private final Long previouslyUsedDeduction;
    private final LocalDate deductionRenewalDate;
}
