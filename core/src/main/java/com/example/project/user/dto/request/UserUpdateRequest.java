package com.example.project.user.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public final class UserUpdateRequest {

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다")
    private final String email;

    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "이름의 앞뒤에 공백을 입력할 수 없습니다")
    private final String name;

    private final LocalDate birthDate;

    @NotBlank(message = "전화번호는 필수입니다")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다")
    private final String phone;

    @Size(max = 255, message = "프로필 이미지는 255자 이하여야 합니다")
    private final String img;

    public String email() {
        return email;
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
