package com.example.project.recipient.dto.response;

import com.example.project.recipient.domain.RecipientVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "수증자 응답")
public class RecipientResponse {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final int ADULT_AGE = 19;

    @ApiModelProperty(value = "수증자 ID", example = "1")
    private Long familyId;
    @ApiModelProperty(value = "수증자 이름", example = "김자녀")
    private String familyName;
    @ApiModelProperty(value = "수증자 관계", allowableValues = "LINEAL_DESCENDANT,OTHER")
    private String relation;

    @ApiModelProperty(value = "수증자 생년월일", example = "2010-01-02")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    private LocalDate birthDate;

    @ApiModelProperty(value = "공제 관계 유형", allowableValues = "LINEAL_DESCENDANT,OTHER")
    private String deductionType;

    /** getter 가 isMinor() 라 Jackson 이 "minor" 로 내보내는 걸 막는다. */
    @ApiModelProperty(value = "조회일 기준 만 19세 미만 여부", example = "true")
    @JsonProperty("isMinor")
    private boolean isMinor;

    @ApiModelProperty(value = "수증자 이미지 경로")
    private String familyImg;

    @ApiModelProperty(value = "등록 시각")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_TIME_FORMAT)
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "최근 수정 시각")
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
