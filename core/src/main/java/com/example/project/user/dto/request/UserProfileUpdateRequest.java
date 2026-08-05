package com.example.project.user.dto.request;

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
@ApiModel(description = "multipart 회원 프로필의 profile JSON")
public final class UserProfileUpdateRequest {

    @ApiModelProperty(value = "수정할 이름", required = true, example = "홍길동")
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "이름 앞뒤에 공백을 입력할 수 없습니다")
    private final String name;

    @ApiModelProperty(value = "수정할 생년월일", required = true, example = "1990-01-01")
    @NotNull(message = "생년월일은 필수입니다")
    @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다")
    private final LocalDate birthDate;

    @ApiModelProperty(value = "수정할 휴대전화 번호", required = true, example = "010-1234-5678")
    @NotBlank(message = "전화번호는 필수입니다")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다")
    @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다")
    private final String phone;
}
