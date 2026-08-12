package com.example.project.common.logging;

import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;

public class DeferredAccessLogInterceptor implements DeferredResultProcessingInterceptor {

    @Override
    public <T> boolean handleTimeout(
            NativeWebRequest request,
            DeferredResult<T> deferredResult
    ) {
        complete(request, "ERROR", "TimeoutException");
        return true;
    }

    @Override
    public <T> boolean handleError(
            NativeWebRequest request,
            DeferredResult<T> deferredResult,
            Throwable throwable
    ) {
        complete(request, "ERROR", throwable.getClass().getSimpleName());
        return true;
    }

    @Override
    public <T> void afterCompletion(
            NativeWebRequest request,
            DeferredResult<T> deferredResult
    ) {
        Object asyncResult = deferredResult.getResult();
        boolean failed = asyncResult instanceof Throwable;

        complete(
                request,
                failed ? "ERROR" : "SUCCESS",
                failed ? asyncResult.getClass().getSimpleName() : null
        );
    }

    private void complete(
            NativeWebRequest request,
            String result,
            String exceptionName
    ) {
        DeferredAccessLogContext context = (DeferredAccessLogContext)
                request.getAttribute(
                        DeferredAccessLogContext.REQUEST_ATTRIBUTE,
                        NativeWebRequest.SCOPE_REQUEST
                );

        if (context != null) {
            context.complete(result, exceptionName);
        }
    }
}
