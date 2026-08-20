package com.example.project.batch;

import com.example.project.batch.config.BatchConfig;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;

public final class BatchApplication {
    private BatchApplication() {
    }

    private static final int EXIT_FAILED = 1;

    public static void main(String[] args) throws Exception {

        BatchStatus status;
        String jobName = resolveJobName(args);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(BatchConfig.class)) {
            JobLauncher jobLauncher = context.getBean(JobLauncher.class);
            Job job = context.getBean(jobName, Job.class);

            JobParametersBuilder parameterBuilder = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis());
            Arrays.stream(args)
                    .filter(argument -> argument.contains("="))
                    .map(argument -> argument.startsWith("--") ? argument.substring(2) : argument)
                    .forEach(argument -> {
                        int separator = argument.indexOf('=');
                        String key = argument.substring(0, separator);
                        String value = argument.substring(separator + 1);
                        if (!"job".equals(key)) {
                            parameterBuilder.addString(key, value);
                        }
                    });

            JobExecution execution = jobLauncher.run(job, parameterBuilder.toJobParameters());
            status = execution.getStatus();

            System.out.println("배치 종료: " + status);
        }

        // 스케줄러(cron 등)는 종료코드로만 성패를 판단한다. 여기서 알려주지 않으면
        // Job 이 FAILED 로 끝나도 성공한 실행과 구분되지 않아 실패가 조용히 묻힌다.
        if (status != BatchStatus.COMPLETED) {
            System.exit(EXIT_FAILED);
        }
    }

    private static String resolveJobName(String[] args) {
        for (String argument : args) {
            String normalized = argument.startsWith("--") ? argument.substring(2) : argument;
            if (normalized.startsWith("job=")) {
                return normalized.substring("job=".length());
            }
        }
        if (args.length > 0 && !args[0].contains("=")) {
            return args[0];
        }
        return "lawArticleJob";
    }
}
