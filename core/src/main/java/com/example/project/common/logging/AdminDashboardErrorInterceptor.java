package com.example.project.common.logging;

import com.example.project.admin.dashboard.service.AdminDashboardErrorRecorder;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdminDashboardErrorInterceptor implements AsyncHandlerInterceptor {

    private static final String START_NANOS_ATTRIBUTE =
            AdminDashboardErrorInterceptor.class.getName() + ".startNanos";
    private static final String RECORDED_ATTRIBUTE =
            AdminDashboardErrorInterceptor.class.getName() + ".recorded";

    private final AdminDashboardErrorRecorder errorRecorder;

    public AdminDashboardErrorInterceptor(AdminDashboardErrorRecorder errorRecorder) {
        this.errorRecorder = errorRecorder;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (isUserApi(request) && request.getAttribute(START_NANOS_ATTRIBUTE) == null) {
            request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
            request.setAttribute(RECORDED_ATTRIBUTE, new AtomicBoolean(false));
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        if (!isUserApi(request)) {
            return;
        }

        ApiErrorTrackingContext.markIfTimeout(request, exception);
        int responseStatus = response.getStatus();
        boolean timeout = ApiErrorTrackingContext.isMarkedTimeout(request);
        if (responseStatus != 422 && responseStatus != 500 && !timeout) {
            return;
        }

        AtomicBoolean recorded = recordedFlag(request);
        if (!recorded.compareAndSet(false, true)) {
            return;
        }

        errorRecorder.record(
                request.getMethod(),
                applicationPath(request),
                responseStatus,
                timeout,
                elapsedMs(request)
        );
    }

    private boolean isUserApi(HttpServletRequest request) {
        String path = applicationPath(request);
        return path.startsWith("/api/")
                && !path.equals("/api/admin")
                && !path.startsWith("/api/admin/")
                && !path.equals("/api/health")
                && !path.startsWith("/api/health/");
    }

    private String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private AtomicBoolean recordedFlag(HttpServletRequest request) {
        Object attribute = request.getAttribute(RECORDED_ATTRIBUTE);
        if (attribute instanceof AtomicBoolean recorded) {
            return recorded;
        }
        AtomicBoolean recorded = new AtomicBoolean(false);
        request.setAttribute(RECORDED_ATTRIBUTE, recorded);
        return recorded;
    }

    private long elapsedMs(HttpServletRequest request) {
        Object attribute = request.getAttribute(START_NANOS_ATTRIBUTE);
        if (!(attribute instanceof Long startNanos)) {
            return 0L;
        }
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
