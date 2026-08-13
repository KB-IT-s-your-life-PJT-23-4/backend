package com.example.project.batch.common.notification;

import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Collectors;

/**
 * 배치가 실패하면 관리자 알림에 남긴다.
 *
 * <p>cron 은 종료코드만 보고 사람은 로그를 안 본다. 실패가 조용히 묻히지 않으려면 관리자 화면에
 * 올라와야 한다. 알림 적재가 실패해도 배치의 원래 실패를 덮지 않도록 예외는 여기서 삼킨다.
 */
@Log4j2
@Component
public class BatchFailureNotifier implements JobExecutionListener {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MESSAGE_LIMIT = 2000;

    private final BatchNotificationMapper batchNotificationMapper;

    public BatchFailureNotifier(BatchNotificationMapper batchNotificationMapper) {
        this.batchNotificationMapper = batchNotificationMapper;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {

    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.FAILED) {
            return;
        }

        String jobName = jobExecution.getJobInstance().getJobName();

        try {
            batchNotificationMapper.insertBatchFailure(
                    jobName + " 배치가 실패했습니다",
                    failureMessage(jobExecution),
                    jobExecution.getId(),
                    "BATCH_FAILURE:" + jobName + ":" + LocalDate.now(SEOUL)
            );
        } catch (RuntimeException e) {
            log.error("배치 실패 알림을 남기지 못했습니다. jobName = {}", jobName, e);
        }
    }

    private String failureMessage(JobExecution jobExecution) {
        String failedSteps = jobExecution.getStepExecutions().stream().filter(step -> step.getStatus() == BatchStatus.FAILED)
                .map(StepExecution::getStepName)
                .collect(Collectors.joining(", "));

        String cause = jobExecution.getAllFailureExceptions().stream()
                .map(Throwable::toString)
                .collect(Collectors.joining(" / "));

        if (cause.isBlank()) {
            cause = jobExecution.getExitStatus().getExitDescription();
        }

        if (cause == null || cause.isBlank()) {
            cause = "실패 원인이 기록되지 않았습니다. 배치 로그를 확인하세요.";
        }

        String message = failedSteps.isBlank() ? cause : "[" + failedSteps + "] " + cause;

        return message.length() > MESSAGE_LIMIT ? message.substring(0, MESSAGE_LIMIT) : message;
    }
}
