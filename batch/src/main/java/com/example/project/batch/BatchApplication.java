package com.example.project.batch;

import com.example.project.batch.config.BatchConfig;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class BatchApplication {
    private BatchApplication() {
    }

    private static final int EXIT_FAILED = 1;

    public static void main(String[] args) throws Exception {

        BatchStatus status;

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(BatchConfig.class)) {
            JobLauncher jobLauncher = context.getBean(JobLauncher.class);
            Job lawArticleJob = context.getBean("lawArticleJob", Job.class);

            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(lawArticleJob, params);
            status = execution.getStatus();

            System.out.println("배치 종료: " + status);
        }

        // 스케줄러(cron 등)는 종료코드로만 성패를 판단한다. 여기서 알려주지 않으면
        // Job 이 FAILED 로 끝나도 성공한 실행과 구분되지 않아 실패가 조용히 묻힌다.
        if (status != BatchStatus.COMPLETED) {
            System.exit(EXIT_FAILED);
        }
    }
}
