package com.example.project.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final int code;
    private final String status;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, ResponseCode responseCode, T data) {
        this.success = success;
        this.code = responseCode.getCode();
        this.status = responseCode.getStatus();
        this.message = responseCode.getMessage();
        this.data = data;
    }

    public static <T> ApiResponse<T> success(ResponseCode responseCode, T data) {
        return new ApiResponse<>(true, responseCode, data);
    }

    public static ApiResponse<Void> success(ResponseCode responseCode) {
        return new ApiResponse<>(true, responseCode, null);
    }

    public static ApiResponse<Void> error(ResponseCode responseCode) {
        return new ApiResponse<>(false, responseCode, null);
    }
}
