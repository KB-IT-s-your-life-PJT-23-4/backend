package com.example.project.batch.product.etf.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class RetryingHttpRequester {

    private final HttpClient httpClient;
    private final Duration readTimeout;
    private final int maxRetries;
    private final long retryDelayMillis;

    public RetryingHttpRequester(
            @Value("${etf.api.connect-timeout-seconds}") int connectTimeoutSeconds,
            @Value("${etf.api.read-timeout-seconds}") int readTimeoutSeconds,
            @Value("${etf.api.max-retries}") int maxRetries,
            @Value("${etf.api.retry-delay-millis:500}") long retryDelayMillis
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        this.readTimeout = Duration.ofSeconds(readTimeoutSeconds);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryDelayMillis = Math.max(0L, retryDelayMillis);
    }

    public String get(URI uri, Map<String, String> headers, String requestDescription) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(readTimeout)
                        .GET();
                headers.forEach(builder::header);

                HttpResponse<String> response = httpClient.send(
                        builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }

                IllegalStateException failure = new IllegalStateException(
                        requestDescription + " API가 HTTP " + response.statusCode() + "를 반환했습니다.");
                if (!isRetryableStatus(response.statusCode()) || attempt == maxRetries) {
                    throw failure;
                }
                lastFailure = failure;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(requestDescription + " API 호출이 중단되었습니다.", exception);
            } catch (IOException exception) {
                lastFailure = new IllegalStateException(requestDescription + " API 호출에 실패했습니다.", exception);
                if (attempt == maxRetries) {
                    throw lastFailure;
                }
            }
            waitBeforeRetry(requestDescription);
        }
        throw lastFailure == null
                ? new IllegalStateException(requestDescription + " API 호출에 실패했습니다.")
                : lastFailure;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void waitBeforeRetry(String requestDescription) {
        if (retryDelayMillis == 0L) {
            return;
        }
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(requestDescription + " API 재시도 대기 중 중단되었습니다.", exception);
        }
    }
}
