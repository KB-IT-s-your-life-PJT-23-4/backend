package com.example.project.batch.product.etf.config;

import com.example.project.batch.common.notification.BatchFailureNotifier;
import com.example.project.batch.product.etf.tasklet.EtfMarketDataTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EtfMarketDataJobConfig {

    @Bean
    public Step etfMarketDataStep(
            StepBuilderFactory stepBuilderFactory,
            EtfMarketDataTasklet tasklet
    ) {
        return stepBuilderFactory.get("etfMarketDataStep")
                .tasklet(tasklet)
                .build();
    }

    @Bean
    public Job etfMarketDataJob(
            JobBuilderFactory jobBuilderFactory,
            Step etfMarketDataStep,
            BatchFailureNotifier failureNotifier
    ) {
        return jobBuilderFactory.get("etfMarketDataJob")
                .listener(failureNotifier)
                .start(etfMarketDataStep)
                .build();
    }
}
