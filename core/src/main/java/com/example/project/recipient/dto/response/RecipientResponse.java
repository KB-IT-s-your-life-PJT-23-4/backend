package com.example.project.recipient.dto.response;

import com.example.project.recipient.domain.RecipientVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipientResponse {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final int ADULT_AGE = 19;

    private Long familyId;
    private String familyName;
    private String relation;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate birthDate;

    private String deductionType;

    /** getter 가 isMinor() 라 Jackson 이 "minor" 로 내보내는 걸 막는다. */
    @JsonProperty("isMinor")
    private boolean isMinor;

    private String familyImg;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_TIME_FORMAT)
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_TIME_FORMAT)
    private LocalDateTime updatedAt;

    public static RecipientResponse from(RecipientVO recipient) {
        return new RecipientResponse(
                recipient.getFamilyId(),
                recipient.getFamilyName(),
                recipient.getRelation(),
                recipient.getBirthDate(),
                recipient.getRelation(),
                isMinor(recipient.getBirthDate()),
                recipient.getFamilyImg(),
                recipient.getCreatedAt(),
                recipient.getUpdatedAt()
        );
    }

    private static boolean isMinor(LocalDate birthDate) {
        if (birthDate == null) {
            return false;
        }

        return Period.between(birthDate, LocalDate.now()).getYears() < ADULT_AGE;
    }
}
