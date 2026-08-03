package com.example.project.common.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Aspect
@Log4j2
public class RequestLoggingAspect {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());


    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "accesstoken",
            "refreshtoken",
            "authorization",
            "token",
            "secret"
    );

    @Around(
            "@within(com.example.project.common.logging.ApiLog) || " +
            "@annotation(com.example.project.common.logging.ApiLog)"
    )
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();

        if (request == null){
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();
        String controller = joinPoint.getTarget()
                .getClass()
                .getSimpleName();
        String controllerMethod = signature.getMethod().getName();

        String requestBody = "-";

        if(shouldLogBody(httpMethod)) {
            requestBody = serializeArguments(joinPoint.getArgs());
        }

        log.info(
                "HTTP_REQUEST method={} uri={} controller={}.{} body={}",
                httpMethod,
                uri,
                controller,
                controllerMethod,
                requestBody
        );

        long startTime = System.nanoTime();

        try {
            Object result = joinPoint.proceed();

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            log.info(
                    "HTTP_RESPONSE method={} uri={} controller={}.{} elapsedMS={} result=SUCCESS",
                    httpMethod,
                    uri,
                    controller,
                    controllerMethod,
                    elapsedMs
            );

            return result;
        } catch (Throwable exception) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            log.warn(
                    "HTTP_RESPONSE method={} uri={} controller={}.{} elapsedMs={} result=ERROR exception={}",
                    httpMethod,
                    uri,
                    controller,
                    controllerMethod,
                    elapsedMs,
                    exception.getClass().getSimpleName()
            );

            throw exception;
        }
    }

    //현재 request 반환
    private HttpServletRequest currentRequest(){
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }

        return attributes.getRequest();
    }

    // 입력 데이터가 있는 Method 판정 (POST, PUT, PATCH)
    private boolean shouldLogBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    //데이터 직렬화 매개변수 -> JSON
    private String serializeArguments(Object[] arguments) {
        try {
            var nodes = Arrays.stream(arguments)
                    .filter(this::isLoggableArgument)
                    .<JsonNode>map(OBJECT_MAPPER::valueToTree)
                    .map(this::maskSensitiveFields)
                    .collect(Collectors.toList());

            if(nodes.isEmpty()) {
                return "-";
            }

            if (nodes.size() == 1) {
                return OBJECT_MAPPER.writeValueAsString(nodes.get(0));
            }

            return OBJECT_MAPPER.writeValueAsString(nodes);
        } catch (Exception e) {
            log.debug("Failed to serialize request arguments", e);
        }

        return "[SERIALIZATION_FAILED";
    }


    private boolean isLoggableArgument(Object argument) {
        return argument != null
                && !(argument instanceof ServletRequest)
                && !(argument instanceof ServletResponse)
                && !(argument instanceof BindingResult)
                && !(argument instanceof MultipartFile)
                && !(argument instanceof Principal);
    }

    //민감 정보 마스킹
    private JsonNode maskSensitiveFields(JsonNode node){
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

            while (fields.hasNext()){
                Map.Entry<String, JsonNode> field = fields.next();

                String fieldName = field.getKey().toLowerCase(Locale.ROOT);

                if(SENSITIVE_FIELDS.contains(fieldName)){
                    objectNode.put(field.getKey(), "******");
                } else {
                    maskSensitiveFields(field.getValue());
                }
            }
        }
        else if (node.isArray()) {
            node.forEach(this::maskSensitiveFields);
        }

        return node;
    }
}
