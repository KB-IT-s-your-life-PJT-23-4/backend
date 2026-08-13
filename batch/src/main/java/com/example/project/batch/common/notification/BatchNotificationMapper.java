package com.example.project.batch.common.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * core 에도 같은 테이블을 읽는 AdminNotificationMapper 가 있다. 이름이 겹치면 batch 모듈이
 * core 의 classpath 에 올라갔을 때 MyBatis 가 만드는 빈 이름(adminNotificationMapper)이 부딪혀
 * 한쪽이 덮인다. 그래서 여기는 Batch 접두어를 붙여 둔다.
 */
@Mapper
public interface BatchNotificationMapper {

    void insertBatchFailure(
            @Param("title") String title,
            @Param("message") String message,
            @Param("jobExecutionId") Long jobExecutionId,

            /** 같은 원인으로 여러 번 실패하는 경우 제거를 위해 남겨놓음 (현재는 주 1회 실행이라 거의 없지만, 정책에 따라
             * 빈도를 조정할 수 있으니 남겨놓음)
             * */
            @Param("dedupeKey") String dedupeKey
    );
}
