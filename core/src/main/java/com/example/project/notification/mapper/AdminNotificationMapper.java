package com.example.project.notification.mapper;

import com.example.project.notification.domain.AdminNotificationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminNotificationMapper {

    /**
     * 읽음 여부는 조회하는 관리자마다 다르므로 adminId 를 조건으로 함께 넘긴다.
     * 필터가 null 이면 그 조건은 걸지 않는다.
     */

    List<AdminNotificationVO> selectNotificationPage(
            @Param("adminId") long adminId,
            @Param("status") String status,
            @Param("notificationType") String notificationType,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("offset") long offset,
            @Param("size") int size
    );

    long countNotifications(
            @Param("adminId") long adminId,
            @Param("status") String status,
            @Param("notificationType") String notificationType,
            @Param("unreadOnly") boolean unreadOnly
    );

    /** 헤더 배지용. 목록 필터와 무관하게 이 관리자가 안 읽은 전체 개수다. */
    long countUnread(@Param("adminId") long adminId);

    /** 이미 읽은 알림을 또 눌러도 실패하지 않도록 INSERT IGNORE 로 넣는다. 그래서 0 은 실패가 아니다. */
    int insertRead(
            @Param("adminNotificationId") long adminNotificationId,
            @Param("adminId") long adminId
    );

    /** OPEN 인 것만 RESOLVED 로 바꾼다. 이미 처리된 건을 다시 눌러도 처리자가 덮이지 않는다. */
    int updateResolved(
            @Param("adminNotificationId") long adminNotificationId,
            @Param("adminId") long adminId
    );
}
