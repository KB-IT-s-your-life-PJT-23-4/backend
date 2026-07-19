package com.example.project.batch.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableBatchProcessing
@ComponentScan(basePackages = "com.example.project.batch")
@PropertySource({"classpath:batch.properties", "classpath:database.properties"})
public class BatchConfig {
}
