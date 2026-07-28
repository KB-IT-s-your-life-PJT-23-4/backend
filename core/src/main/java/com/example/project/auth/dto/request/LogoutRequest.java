package com.example.project.auth.dto.request;

import javax.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh Token은 필수입니다")
        String refreshToken
) {
}
