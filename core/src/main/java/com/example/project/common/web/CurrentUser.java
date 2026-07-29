package com.example.project.common.web;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;

/**
 * JwtAuthFilter 가 SecurityContext 에 넣어둔 principal(문자열 userId)을 꺼내 쓴다.
 * 인증되지 않은 요청은 여기서 걸러진다.
 */
public final class CurrentUser {

    private static final String ANONYMOUS = "anonymousUser";

    private CurrentUser() {
    }

    public static Long id(String principal) {
        if (principal == null || principal.equals(ANONYMOUS)) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException e) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }
    }
}
