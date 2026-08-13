package com.example.project.admin.batch.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Spring Batch 메타테이블(BATCH_JOB_EXECUTION / BATCH_JOB_INSTANCE / BATCH_STEP_EXECUTION)에서
 * 화면에 필요한 값만 뽑아 담는다. 실행 이력을 위한 별도 테이블은 두지 않는다.
 */
@Data
public class BatchJobExecutionVO {

    private Long jobExecutionId;
    private Long jobInstanceId;
    private String jobName;

    private String status;
    private String exitCode;
    private String exitMessage;

    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** Step 이 여러 개인 잡이 있어 건수는 전부 합산값이다. lawArticleJob 은 Step 2개. */
    private long readCount;
    private long writeCount;
    private long skipCount;
    private int stepCount;
}
