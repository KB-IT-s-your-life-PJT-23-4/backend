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
@ApiModel(description = "로그아웃 요청")
public final class LogoutRequest {

    @ApiModelProperty(value = "폐기할 Refresh Token", required = true)
    @NotBlank(message = "Refresh Token은 필수입니다")
    private final String refreshToken;

    public String refreshToken() {
        return refreshToken;
    }
}
