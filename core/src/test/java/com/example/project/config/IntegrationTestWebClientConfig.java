package com.example.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class IntegrationTestWebClientConfig {

    @Bean
    @Primary
    public WebClient integrationTestPrimaryWebClient() {
        return WebClient.builder().build();
    }
}
