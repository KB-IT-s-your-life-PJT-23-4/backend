package com.example.project.batch;

import com.example.project.batch.config.BatchConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class BatchApplication {
    private BatchApplication() {
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(BatchConfig.class)) {
            JobLauncher jobLauncher = context.getBean(JobLauncher.class);
            Job lawArticleJob = context.getBean("lawArticleJob", Job.class);

            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(lawArticleJob, params);
            System.out.println("배치 종료: " + execution.getStatus());
        }
    }
}
