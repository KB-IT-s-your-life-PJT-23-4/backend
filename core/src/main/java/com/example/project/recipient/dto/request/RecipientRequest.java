package com.example.project.recipient.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
@RequiredArgsConstructor
@ApiModel(description = "수증자 등록 및 JSON 부분 수정 요청")
public class RecipientRequest {

    @ApiModelProperty(value = "수증자 이름", example = "김자녀")
    @Size(min = 2, max = 100, message = "수증자 이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "수증자 이름 앞뒤에 공백을 입력할 수 없습니다")
    private String familyName;

    @ApiModelProperty(
            value = "수증자 관계",
            allowableValues = "LINEAL_DESCENDANT,OTHER",
            example = "LINEAL_DESCENDANT"
    )
    @Pattern(regexp = "LINEAL_DESCENDANT|OTHER", message = "지원하지 않는 수증자 관계입니다")
    private String relation;

    @ApiModelProperty(value = "수증자 생년월일", example = "2010-01-02")
    @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다")
    private LocalDate birthDate;

    @ApiModelProperty(value = "수증자 이미지 경로")
    @Size(max = 255, message = "프로필 이미지는 255자 이하여야 합니다")
    private String familyImg;

}
