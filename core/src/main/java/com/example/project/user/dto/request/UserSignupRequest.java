package com.example.project.user.dto.request;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public record UserSignupRequest(
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다")
        String password,

        @NotBlank(message = "이름은 필수입니다")
        @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다")
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "이름의 앞뒤에 공백을 입력할 수 없습니다")
        String name
) {
}
