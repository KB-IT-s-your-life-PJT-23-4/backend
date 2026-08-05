package com.example.project.user.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@ApiModel(description = "회원가입 요청")
public final class UserSignupRequest {

    @ApiModelProperty(value = "로그인 이메일", required = true, example = "user@example.com")
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다")
    private final String email;

    @ApiModelProperty(value = "8~64자 영문·숫자·특수문자 조합", required = true, example = "password123!")
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E])[\\x21-\\x7E]+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다"
    )
    private final String password;

    @ApiModelProperty(value = "회원 이름", required = true, example = "홍길동")
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "이름의 앞뒤에 공백을 입력할 수 없습니다")
    private final String name;

    @ApiModelProperty(value = "생년월일", example = "1990-01-01")
    @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다")
    private final LocalDate birthDate;

    @ApiModelProperty(value = "휴대전화 번호", required = true, example = "010-1234-5678")
    @NotBlank(message = "전화번호는 필수입니다")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다")
    @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다")
    private final String phone;

    @ApiModelProperty(value = "프로필 이미지 경로", example = "/api/profile-images/example.png")
    @Size(max = 255, message = "프로필 이미지는 255자 이하여야 합니다")
    private final String img;

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public String name() {
        return name;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public String phone() {
        return phone;
    }

    public String img() {
        return img;
    }
}
