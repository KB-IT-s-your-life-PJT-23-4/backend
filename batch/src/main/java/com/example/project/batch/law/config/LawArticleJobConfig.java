package com.example.project.batch.law.config;

import com.example.project.batch.law.chunk.LawUnit;
import com.example.project.batch.law.chunk.processor.LawArticleItemProcessor;
import com.example.project.batch.law.chunk.reader.LawArticleItemReader;
import com.example.project.batch.law.chunk.writer.LawArticleItemWriter;
import com.example.project.batch.law.dto.LawArticle;
import com.example.project.batch.law.tasklet.LawJsonDownloadTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LawArticleJobConfig {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final int chunkSize;

    public LawArticleJobConfig(JobBuilderFactory jobBuilderFactory,
                               StepBuilderFactory stepBuilderFactory,
                               @Value("${batch.chunk.size}") int chunkSize) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.chunkSize = chunkSize;
    }

    /** Step 1. 법령 원문 JSON 을 내려받아 파일로 저장한다. */
    @Bean
    public Step lawDownloadStep(LawJsonDownloadTasklet tasklet) {
        return stepBuilderFactory.get("lawDownloadStep")
                .tasklet(tasklet)
                .build();
    }

    /** Step 2. 저장된 JSON 을 읽어 조문·별표 단위로 가공한 뒤 law_article 에 적재한다. */
    @Bean
    public Step lawArticleStep(LawArticleItemReader reader,
                               LawArticleItemProcessor processor,
                               LawArticleItemWriter writer) {
        return stepBuilderFactory.get("lawArticleStep")
                .<LawUnit, LawArticle>chunk(chunkSize)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /**
     * Step 1 이 성공해야 Step 2 로 넘어간다. next() 는 이전 Step 실패 시 Job 을 중단시킨다.
     * 다운로드가 실패하면 옛 파일로 가공하지 않도록 여기서 끊는 것이 중요하다.
     */
    @Bean
    public Job lawArticleJob(Step lawDownloadStep, Step lawArticleStep) {
        return jobBuilderFactory.get("lawArticleJob")
                .start(lawDownloadStep)
                .next(lawArticleStep)
                .build();
    }
}
