package com.example.project.admin.batch.dto.response;

import com.example.project.admin.batch.domain.BatchJobExecutionVO;
import com.example.project.common.api.Pagination;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class BatchJobExecutionPageResponse {

    private final List<BatchJobExecutionVO> executions;

    private final Pagination pagination;
}
