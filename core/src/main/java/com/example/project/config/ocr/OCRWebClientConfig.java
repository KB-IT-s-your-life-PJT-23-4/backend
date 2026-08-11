package com.example.project.config.ocr;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OCRWebClientConfig {

    @Value("${clova.ocr.invoke-url:}")
    private String invokeUrl;

    @Value("${clova.ocr.secret:}")
    private String secret;

    @Value("${clova.ocr.max-size-bytes:10485760}")
    private long maxSizeBytes;

    @Bean
    public ClovaOcrProperties ocrProperties() {
        return new ClovaOcrProperties(invokeUrl, secret, maxSizeBytes);
    }

    @Bean
    public WebClient clovaOcrWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024)).build();
    }
}
