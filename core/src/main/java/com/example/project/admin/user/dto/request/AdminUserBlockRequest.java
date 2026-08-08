package com.example.project.admin.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@ApiModel(description = "관리자 회원 차단 요청")
public class AdminUserBlockRequest {

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @ApiModelProperty(value = "차단 만료 시각", example = "2026-08-31T23:59:59", required = true)
    private LocalDateTime blockedUntil;

    public AdminUserBlockRequest(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }
}
