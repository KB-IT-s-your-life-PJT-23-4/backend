package com.example.project.auth.dto.response;

import com.example.project.user.dto.UserDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public final class AuthTokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final UserDTO user;

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public UserDTO user() {
        return user;
    }
}
