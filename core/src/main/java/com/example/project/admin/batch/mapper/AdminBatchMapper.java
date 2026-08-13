package com.example.project.admin.batch.mapper;

import com.example.project.admin.batch.domain.BatchJobExecutionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminBatchMapper {

    /** 필터가 null 이면 그 조건은 걸지 않는다. */
    List<BatchJobExecutionVO> selectExecutionPage(
            @Param("jobName") String jobName,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("size") int size
    );

    long countExecutions(
            @Param("jobName") String jobName,
            @Param("status") String status
    );

    /** 잡 카드에 붙일 마지막 실행. 한 번도 안 돌린 잡이면 null 이다. */
    BatchJobExecutionVO selectLatestExecution(@Param("jobName") String jobName);

    /**
     * 재시작 가능 여부를 판단하려면 실행의 잡 이름과 상태가 필요하다.
     * 없는 ID 면 null 이라 404 와 409 를 갈라서 돌려줄 수 있다.
     */
    BatchJobExecutionVO selectExecution(@Param("jobExecutionId") long jobExecutionId);
}
