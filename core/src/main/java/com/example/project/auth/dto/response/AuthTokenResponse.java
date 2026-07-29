package com.example.project.auth.dto.response;

import com.example.project.user.dto.UserDTO;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        UserDTO user
) {
}
