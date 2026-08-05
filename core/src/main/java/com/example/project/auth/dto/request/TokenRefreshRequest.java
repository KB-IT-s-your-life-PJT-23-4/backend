package com.example.project.auth.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@ApiModel(description = "토큰 재발급 요청")
public final class TokenRefreshRequest {

    @ApiModelProperty(value = "로그인 또는 직전 재발급에서 받은 Refresh Token", required = true)
    @NotBlank(message = "Refresh Token은 필수입니다")
    private final String refreshToken;

    public String refreshToken() {
        return refreshToken;
    }
}
