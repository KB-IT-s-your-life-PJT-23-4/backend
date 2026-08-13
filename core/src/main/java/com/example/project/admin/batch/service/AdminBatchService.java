package com.example.project.admin.batch.service;

import com.example.project.admin.batch.domain.BatchJobExecutionVO;
import com.example.project.admin.batch.dto.response.BatchJobExecutionPageResponse;
import com.example.project.admin.batch.dto.response.BatchJobResponse;
import com.example.project.admin.batch.mapper.AdminBatchMapper;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminBatchService {

    /**
     * 실행 가능한 잡 화이트리스트. 요청으로 들어온 이름을 그대로 빈 조회에 넘기면 관리자가
     * 아무 빈이나 잡으로 띄울 수 있게 된다. 잡이 늘어나면 여기에 추가한다.
     */
    private static final Map<String, String> RUNNABLE_JOBS = Map.of(
            "lawArticleJob", "법령 조문 수집"
    );

    /** BATCH_JOB_EXECUTION.STATUS 에 실제로 들어가는 값들(BatchStatus enum). */
    private static final Set<String> EXECUTION_STATUSES = Set.of(
            "COMPLETED", "STARTING", "STARTED", "STOPPING", "STOPPED", "FAILED", "ABANDONED", "UNKNOWN"
    );

    /** 재시작은 끝난 실행에만 의미가 있다. 성공한 잡은 재시작 대상이 아니다. */
    private static final Set<String> RESTARTABLE_STATUSES = Set.of("FAILED", "STOPPED");

    private final AdminBatchMapper adminBatchMapper;
    private final BatchJobRunner batchJobRunner;

    /** 잡 카드 목록. 한 번도 안 돌린 잡은 lastExecution 이 null 로 나간다. */
    @Transactional(readOnly = true)
    public List<BatchJobResponse> getJobList() {
        return RUNNABLE_JOBS.entrySet().stream()
                .map(job -> BatchJobResponse.builder()
                        .jobName(job.getKey())
                        .displayName(job.getValue())
                        .running(batchJobRunner.isRunning(job.getKey()))
                        .lastExecution(adminBatchMapper.selectLatestExecution(job.getKey()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchJobExecutionPageResponse getPageExecutionList(
            int page,
            int size,
            String jobNameValue,
            String statusValue
    ) {
        long offset = Pagination.calculateOffset(page, size);

        String jobName = normalizeJobFilter(jobNameValue);
        String status = normalizeStatusFilter(statusValue);

        long totalElements = adminBatchMapper.countExecutions(jobName, status);

        List<BatchJobExecutionVO> executions =
                adminBatchMapper.selectExecutionPage(jobName, status, offset, size);

        return BatchJobExecutionPageResponse.builder()
                .executions(executions)
                .pagination(Pagination.of(page, size, totalElements, executions.size()))
                .build();
    }

    /** 실행 접수. 실제 실행은 백그라운드라 여기서는 받아줄 수 있는지만 판단한다. */
    public void run(String jobNameValue) {
        String jobName = requireRunnableJob(jobNameValue);

        if (!batchJobRunner.run(jobName)) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }
    }

    /**
     * 재시작 접수. 실행이 없으면 404, 성공했거나 아직 돌고 있는 실행이면 409 로 가른다.
     * 백그라운드에서 뒤늦게 거절당하면 관리자가 이유를 알 방법이 없어 미리 확인한다.
     */
    public void restart(long jobExecutionId) {
        if (jobExecutionId <= 0) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        BatchJobExecutionVO execution = adminBatchMapper.selectExecution(jobExecutionId);

        if (execution == null) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }

        String jobName = requireRunnableJob(execution.getJobName());

        if (!RESTARTABLE_STATUSES.contains(execution.getStatus())) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }

        if (!batchJobRunner.restart(jobName, jobExecutionId)) {
            throw new ServiceException(ResponseCode.CONFLICT);
        }
    }

    private String requireRunnableJob(String jobName) {
        if (jobName == null || !RUNNABLE_JOBS.containsKey(jobName)) {
            throw new ServiceException(ResponseCode.RESOURCE_NOT_FOUND);
        }

        return jobName;
    }

    private String normalizeJobFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return requireRunnableJob(value.trim());
    }

    private String normalizeStatusFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        if (!EXECUTION_STATUSES.contains(normalized)) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        return normalized;
    }
}
