package com.example.project.batch.config;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 관리자 화면의 "실패 지점부터 재시작"용 설정.
 *
 * <p>{@code @EnableBatchProcessing} 은 JobRepository/JobExplorer/JobRegistry 까지만 만들어 준다.
 * 재시작은 실행 ID 로 이전 JobParameters 를 되살려 같은 JobInstance 를 이어 돌리는 동작이라
 * JobOperator 가 따로 필요하다. 매번 새 JobInstance 를 만드는 cron 경로에는 영향이 없다.
 */
@Configuration
public class BatchOperatorConfig {

    /**
     * JobOperator 는 잡을 이름으로 찾지만 Job 빈이 레지스트리에 자동으로 담기지는 않는다.
     * 흔히 쓰는 JobRegistryBeanPostProcessor 는 여기서 쓸 수 없다. BeanPostProcessor 라
     * 일반 빈보다 훨씬 먼저 만들어지는데, 이 컨텍스트에는 PropertySourcesPlaceholderConfigurer 가
     * 없어 그 시점에는 ${jdbc.driver} 같은 자리표시자가 아직 치환되지 않는다(DataSource 생성 실패).
     * 그래서 등록은 재시작을 요청하는 쪽에서 직접 한다.
     */
    @Bean
    public JobOperator jobOperator(JobLauncher jobLauncher,
                                   JobRepository jobRepository,
                                   JobExplorer jobExplorer,
                                   JobRegistry jobRegistry) {
        SimpleJobOperator jobOperator = new SimpleJobOperator();

        jobOperator.setJobLauncher(jobLauncher);
        jobOperator.setJobRepository(jobRepository);
        jobOperator.setJobExplorer(jobExplorer);
        jobOperator.setJobRegistry(jobRegistry);

        return jobOperator;
    }
}
