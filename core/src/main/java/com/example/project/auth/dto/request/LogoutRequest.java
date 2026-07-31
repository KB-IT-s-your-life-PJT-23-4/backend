package com.example.project.auth.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public final class LogoutRequest {

    @NotBlank(message = "Refresh Token은 필수입니다")
    private final String refreshToken;

    public String refreshToken() {
        return refreshToken;
    }
}
