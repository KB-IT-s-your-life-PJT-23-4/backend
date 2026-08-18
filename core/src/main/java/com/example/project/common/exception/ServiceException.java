package com.example.project.common.exception;

import com.example.project.common.api.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class ServiceException extends RuntimeException{

    private final ResponseCode responseCode;
    public ServiceException(ResponseCode responseCode){

        super(responseCode.getMessage());
        this.responseCode = responseCode;
    }

    public ServiceException(ResponseCode responseCode, Throwable cause) {
        super(responseCode.getMessage(), cause);
        this.responseCode = responseCode;
    }
}
