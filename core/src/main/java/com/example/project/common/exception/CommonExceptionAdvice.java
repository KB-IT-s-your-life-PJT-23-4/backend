package com.example.project.common.exception;


import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
@Log4j2
public class CommonExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public String handleException(Exception exception, Model model) {
        log.error("Unhandled exception", exception);
        model.addAttribute("exception", exception);
        return "error_page";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handle404(NoHandlerFoundException exception, Model model, HttpServletRequest request) {
        log.warn("No handler for {}", request.getRequestURI(), exception);
        model.addAttribute("uri", request.getRequestURI());
        return "custom404";
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceException(ServiceException e, HttpServletRequest request){
        ResponseCode responseCode = e.getResponseCode();

        ApiResponse<Void> res = ApiResponse.error(responseCode, request.getRequestURI());

        return ResponseEntity
                .status(resolveHttpStatus(responseCode))
                .body(res);
    }

    private HttpStatus resolveHttpStatus(ResponseCode responseCode){
        int code = responseCode.getCode();

        if (code >= 500) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (code) {
            case 401, 402 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404, 410, 411, 412, 415 -> HttpStatus.NOT_FOUND;
            case 405 -> HttpStatus.METHOD_NOT_ALLOWED;
            case 408 -> HttpStatus.REQUEST_TIMEOUT;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
