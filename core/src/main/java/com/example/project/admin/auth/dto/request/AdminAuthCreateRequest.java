package com.example.project.admin.auth.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "관리자 계정 생성 요청")
public class AdminAuthCreateRequest {

    @ApiModelProperty(value = "관리자 로그인 이메일", required = true, example = "admin@example.com")
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다")
    private String email;

    @ApiModelProperty(value = "초기 로그인 비밀번호", required = true, example = "Admin1234!")
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E])[\\x21-\\x7E]+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다"
    )
    private String password;

    @ApiModelProperty(value = "관리자 이름", required = true, example = "홍길동")
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "이름의 앞뒤에 공백을 입력할 수 없습니다")
    private String name;

    @ApiModelProperty(value = "관리자 전화번호", required = true, example = "010-1234-5678")
    @NotBlank(message = "전화번호는 필수입니다")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다")
    @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다")
    private String phone;

    @ApiModelProperty(value = "관리자 역할", required = true, allowableValues = "ROOT, MIDDLE, DEFAULT")
    @NotBlank(message = "관리자 역할은 필수입니다")
    private String role;
}
