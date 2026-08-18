package com.example.project.common.logging;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import org.springframework.web.context.request.NativeWebRequest;

import javax.servlet.http.HttpServletRequest;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public final class ApiErrorTrackingContext {

    private static final String TIMEOUT_ATTRIBUTE =
            ApiErrorTrackingContext.class.getName() + ".timeout";

    private ApiErrorTrackingContext() {
    }

    public static void markTimeout(HttpServletRequest request) {
        if (request != null) {
            request.setAttribute(TIMEOUT_ATTRIBUTE, Boolean.TRUE);
        }
    }

    public static void markTimeout(NativeWebRequest request) {
        if (request != null) {
            request.setAttribute(TIMEOUT_ATTRIBUTE, Boolean.TRUE, NativeWebRequest.SCOPE_REQUEST);
        }
    }

    public static void markIfTimeout(HttpServletRequest request, Throwable throwable) {
        if (isTimeout(throwable)) {
            markTimeout(request);
        }
    }

    public static void markIfTimeout(NativeWebRequest request, Throwable throwable) {
        if (isTimeout(throwable)) {
            markTimeout(request);
        }
    }

    public static boolean isMarkedTimeout(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(TIMEOUT_ATTRIBUTE));
    }

    public static boolean isTimeout(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;

        while (current != null && visited.add(current)) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }

            if (current instanceof ServiceException serviceException) {
                ResponseCode responseCode = serviceException.getResponseCode();
                if (responseCode == ResponseCode.REQUEST_TIMEOUT) {
                    return true;
                }
                if (responseCode == ResponseCode.EXTERNAL_API_TIMEOUT) {
                    return true;
                }
            }

            String simpleName = current.getClass().getSimpleName();
            if (simpleName.endsWith("TimeoutException")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
