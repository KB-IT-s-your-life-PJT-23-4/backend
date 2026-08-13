package com.example.project.admin.batch.dto.response;

import com.example.project.admin.batch.domain.BatchJobExecutionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class BatchJobResponse {

    private final String jobName;

    private final String displayName;

    /**
     * 이 서버에서 지금 돌고 있는지. 메타테이블의 STARTED 는 톰캣이 죽으면 그대로 남아 있어서
     * 버튼을 잠그는 근거로 쓰기에 부적절하다. 그래서 실행 중 여부는 메모리 상태로 판단한다.
     */
    private final boolean running;

    private final BatchJobExecutionVO lastExecution;
}
