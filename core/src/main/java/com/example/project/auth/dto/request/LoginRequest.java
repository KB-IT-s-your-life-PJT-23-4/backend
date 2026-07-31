package com.example.project.auth.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public final class LoginRequest {

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다")
    private final String email;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(max = 64, message = "비밀번호는 64자 이하여야 합니다")
    private final String password;

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }
}
