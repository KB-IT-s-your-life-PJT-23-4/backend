package com.example.project.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(description = "모든 REST API가 사용하는 공통 응답")
public class ApiResponse<T> {

    @ApiModelProperty(value = "애플리케이션 응답 코드", example = "200")
    private final int statusCode;
    @ApiModelProperty(value = "응답 생성 시각", example = "2026-08-05T11:30:00")
    private final LocalDateTime timestamp;
    @ApiModelProperty(value = "요청 URI", example = "/api/users/me")
    private final String path;
    @ApiModelProperty(value = "성공 응답 데이터")
    private final T data;
    @ApiModelProperty(value = "성공 메시지", example = "요청이 정상적으로 처리되었습니다")
    private final String message;
    @ApiModelProperty(value = "실패 메시지", example = "인증 정보가 없거나 유효하지 않습니다")
    private final String error;

    private ApiResponse(ResponseCode responseCode, String path, T data, boolean success) {
        this.statusCode = responseCode.getCode();
        this.timestamp = LocalDateTime.now();
        this.path = path;
        this.data = data;

        if (success) {
            this.message = responseCode.getMessage();
            this.error = null;
        } else {
            this.message = null;
            this.error = responseCode.getMessage();
        }
    }

    private ApiResponse(
            int statusCode,
            String path,
            T data,
            String message,
            String error
    ) {
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now();
        this.path = path;
        this.data = data;
        this.message = message;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(ResponseCode responseCode, String path, T data) {
        return new ApiResponse<>(responseCode, path, data, true);
    }

    public static ApiResponse<Void> error(ResponseCode responseCode, String path) {
        return new ApiResponse<>(responseCode, path, null, false);
    }

    public static <T> ApiResponse<T> success(
            int statusCode,
            String path,
            T data,
            String message
    ) {
        return new ApiResponse<>(statusCode, path, data, message, null);
    }

    public static ApiResponse<Void> error(
            int statusCode,
            String path,
            String message,
            String error
    ) {
        return new ApiResponse<>(statusCode, path, null, message, error);
    }

    public static <T> ApiResponse<T> error(
            int statusCode,
            String path,
            String message,
            String error,
            T data
    ) {
        return new ApiResponse<>(statusCode, path, data, message, error);
    }
}
