package com.example.project.user.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@ApiModel(description = "이메일 사용 가능 여부")
public final class EmailAvailabilityResponse {

    @ApiModelProperty(value = "true이면 가입 가능", example = "true")
    private final boolean available;

    public boolean available() {
        return available;
    }
}
