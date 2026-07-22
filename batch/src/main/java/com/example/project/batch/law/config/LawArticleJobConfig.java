package com.example.project.batch.law.config;

import com.example.project.batch.law.dto.LawArticle;
import com.example.project.batch.law.reader.LawArticleItemReader;
import com.example.project.batch.law.writer.LawArticleItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LawArticleJobConfig {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    public LawArticleJobConfig(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
    }

    @Bean
    public Step lawArticleStep(LawArticleItemWriter writer) {
        return stepBuilderFactory.get("lawArticleStep")
                .<LawArticle, LawArticle>chunk(100)
                .reader(LawArticleItemReader.mock())
                .writer(writer)
                .build();
    }

    @Bean
    public Job lawArticleJob(Step lawArticleStep) {
        return jobBuilderFactory.get("lawArticleJob")
                .start(lawArticleStep)
                .build();
    }
}
