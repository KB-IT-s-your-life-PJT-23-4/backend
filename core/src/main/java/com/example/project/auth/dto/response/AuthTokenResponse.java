package com.example.project.auth.dto.response;

import com.example.project.user.dto.UserDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@ApiModel(description = "로그인 및 토큰 재발급 결과")
public final class AuthTokenResponse {

    @ApiModelProperty(value = "Authorization 헤더에 사용할 Access Token")
    private final String accessToken;
    @ApiModelProperty(value = "Access Token 재발급에 사용할 Refresh Token")
    private final String refreshToken;
    @ApiModelProperty(value = "인증된 회원 정보")
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
