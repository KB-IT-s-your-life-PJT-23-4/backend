package com.example.project.common.logging;

import com.example.project.admin.access.domain.AdminAccessEvent;
import com.example.project.admin.access.service.AdminAccessSseService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.DeferredResult;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RequestLoggingAspectTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("DeferredResult는 컨트롤러 반환 시점이 아니라 비동기 완료 시점에 성공을 기록한다")
    void publishesDeferredResultWhenAsyncRequestCompletes() throws Throwable {
        CapturingSseService sseService = new CapturingSseService();
        RequestLoggingAspect aspect = new RequestLoggingAspect(
                sseService,
                Clock.fixed(
                        Instant.parse("2026-08-12T00:00:00Z"),
                        ZoneId.of("Asia/Seoul")
                )
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/consult");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        DeferredResult<String> deferredResult = new DeferredResult<>();
        Object returned = aspect.logRequest(joinPointReturning(deferredResult));

        assertSame(deferredResult, returned);
        assertEquals(0, sseService.events.size());

        deferredResult.setResult("완료");
        new DeferredAccessLogInterceptor().afterCompletion(
                new ServletWebRequest(request),
                deferredResult
        );

        assertEquals(1, sseService.events.size());
        assertEquals("SUCCESS", sseService.events.get(0).getResult());
    }

    private ProceedingJoinPoint joinPointReturning(Object result) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("asyncEndpoint");
        MethodSignature signature = proxy(
                MethodSignature.class,
                (proxy, calledMethod, arguments) -> {
                    if ("getMethod".equals(calledMethod.getName())) {
                        return method;
                    }
                    return defaultValue(calledMethod.getReturnType());
                }
        );

        TestController target = new TestController();
        return proxy(
                ProceedingJoinPoint.class,
                (proxy, calledMethod, arguments) -> switch (calledMethod.getName()) {
                    case "proceed" -> result;
                    case "getSignature" -> signature;
                    case "getTarget" -> target;
                    case "getArgs" -> new Object[0];
                    default -> defaultValue(calledMethod.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static class TestController {

        public DeferredResult<String> asyncEndpoint() {
            return null;
        }
    }

    private static class CapturingSseService extends AdminAccessSseService {

        private final List<AdminAccessEvent> events = new ArrayList<>();

        private CapturingSseService() {
            super(Runnable::run);
        }

        @Override
        public void publish(AdminAccessEvent event) {
            events.add(event);
        }
    }
}
