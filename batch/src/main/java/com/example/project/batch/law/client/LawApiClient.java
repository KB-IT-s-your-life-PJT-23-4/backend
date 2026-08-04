package com.example.project.batch.law.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// http 호출 + 응답 얻기
@Component
@Log4j2
public class LawApiClient {

    private static final String ROOT_FIELD = "법령";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String oc;
    private final Duration timeout;

    public LawApiClient(@Value("${law.api.base-url}") String baseUrl, @Value("${law.api.oc}") String oc,
                        @Value("${law.api.timeout-seconds}") int timeoutSeconds
    ) {
        this.baseUrl = baseUrl;
        this.oc = oc;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public String fetchLawJson(String lawId) {
        URI uri = URI.create(baseUrl + "?OC=" + encode(oc) + "&target=law&type=JSON&ID=" + encode(lawId));

        HttpRequest req = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        HttpResponse<String> res;

        try {
            res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("법령 api 호출 중단, 법령 id=" + lawId, e);
        } catch (IOException e) {
            throw new IllegalStateException("법령 api 호출 실패, 법령 id=" + lawId, e);
        }

        if (res.statusCode() != 200) {
            throw new IllegalStateException("법령 api가 http " + res.statusCode() + "를 반환하였습니다, 법령 id=" + lawId);
        }

        String body = res.body();
        verify(body, lawId);

        log.info("법령 JSON 수신: 법령ID={}, {}KB",
                lawId, body.getBytes(StandardCharsets.UTF_8).length / 1024);
        return body;

    }

    private void verify(String body, String lawId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("법령 api 응답이 JSON이 아닙니다, 법령 id=" + lawId, e);
        }

        if (root.has(ROOT_FIELD)) {
            return;
        }

        throw new IllegalStateException("법령 api가 오류를 반환하였습니다. 법령 id=" + lawId + ", 응답=" + body.substring(0,
                Math.min(body.length(), 200)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
