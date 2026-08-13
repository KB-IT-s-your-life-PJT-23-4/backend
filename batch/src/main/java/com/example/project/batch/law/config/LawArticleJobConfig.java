package com.example.project.batch.law.config;

import com.example.project.batch.common.notification.BatchFailureNotifier;
import com.example.project.batch.law.chunk.LawUnit;
import com.example.project.batch.law.chunk.processor.LawArticleItemProcessor;
import com.example.project.batch.law.chunk.reader.LawArticleItemReader;
import com.example.project.batch.law.chunk.writer.LawArticleItemWriter;
import com.example.project.batch.law.chunk.writer.LawArticleJsonlAggregator;
import com.example.project.batch.law.dto.LawArticle;
import com.example.project.batch.law.tasklet.LawJsonDownloadTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

    /*
     * jsonl 산출물. 임베딩 파이프라인이 읽어 간다.
     * <p>매 실행마다 새로 쓴다(FlatFileItemWriter 기본값). 이어 붙이면 지난주 조문이 그대로 남아
     * 삭제된 조문까지 임베딩된다.
     */
    @Bean
    public FlatFileItemWriter<LawArticle> lawArticleJsonlWriter(@Value("${law.jsonl.path}") String jsonlPath) {
        return new FlatFileItemWriterBuilder<LawArticle>()
                .name("lawArticleJsonlWriter")
                .resource(new FileSystemResource(jsonlPath))
                .encoding(StandardCharsets.UTF_8.name())
                .lineAggregator(new LawArticleJsonlAggregator())
                .build();
    }

    /*
     * DB 와 jsonl 을 같은 청크에서 함께 쓴다. 둘을 따로 돌리면 사이에 배치가 다시 실행됐을 때
     * 한쪽만 최신이 되어 벡터 스토어와 원문이 어긋난다.
     * <p>둘 중 하나가 실패하면 청크 트랜잭션이 롤백되므로 반쪽만 반영된 상태도 남지 않는다.
     */
    @Bean
    public CompositeItemWriter<LawArticle> lawArticleCompositeWriter(LawArticleItemWriter dbWriter,
                                                                     FlatFileItemWriter<LawArticle> jsonlWriter) {
        CompositeItemWriter<LawArticle> writer = new CompositeItemWriter<>();

        writer.setDelegates(List.of(dbWriter, jsonlWriter));

        return writer;
    }

    /** Step 2. 저장된 JSON 을 읽어 조문·별표 단위로 가공한 뒤 law_article 과 jsonl 에 적재한다. */
    @Bean
    public Step lawArticleStep(LawArticleItemReader reader,
                               LawArticleItemProcessor processor,
                               CompositeItemWriter<LawArticle> writer) {
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
    public Job lawArticleJob(Step lawDownloadStep, Step lawArticleStep, BatchFailureNotifier failureNotifier) {
        return jobBuilderFactory.get("lawArticleJob")
                .listener(failureNotifier)
                .start(lawDownloadStep)
                .next(lawArticleStep)
                .build();
    }
}
