package com.example.project.recipient.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@ApiModel(description = "multipart 수증자 프로필의 profile JSON")
public final class RecipientProfileUpdateRequest {

    @ApiModelProperty(value = "수정할 수증자 이름", required = true, example = "김자녀")
    @NotBlank(message = "수증자 이름은 필수입니다")
    @Size(min = 2, max = 100, message = "수증자 이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "수증자 이름 앞뒤에 공백을 입력할 수 없습니다")
    private final String familyName;

    @ApiModelProperty(value = "수정할 생년월일", required = true, example = "2010-01-02")
    @NotNull(message = "생년월일은 필수입니다")
    @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다")
    private final LocalDate birthDate;
}
