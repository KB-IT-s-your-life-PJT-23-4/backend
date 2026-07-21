package com.example.project.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.Api;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int statusCode;
    private final LocalDateTime timestamp;
    private final String path;
    private final T data;
    private final String message;
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

    public static <T> ApiResponse<T> success(ResponseCode responseCode, String path, T data) {
        return new ApiResponse<>(responseCode, path, data, true);
    }

    public static ApiResponse<Void> error(ResponseCode responseCode, String path) {
        return new ApiResponse<>(responseCode, path, null, false);
    }
}
