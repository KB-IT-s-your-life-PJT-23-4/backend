package com.example.project.user.dto.request;

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
public final class UserProfileUpdateRequest {

    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "이름 앞뒤에 공백을 입력할 수 없습니다")
    private final String name;

    @NotNull(message = "생년월일은 필수입니다")
    @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다")
    private final LocalDate birthDate;

    @NotBlank(message = "전화번호는 필수입니다")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다")
    private final String phone;
}
