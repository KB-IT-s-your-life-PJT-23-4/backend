package com.example.project.admin.dashboard.mapper;

import com.example.project.admin.dashboard.domain.DailySignupCount;
import com.example.project.admin.dashboard.domain.AdminDashboardErrorCount;
import com.example.project.admin.dashboard.domain.ConsultationDashboardCount;
import com.example.project.admin.dashboard.domain.LatestProductDataVersion;
import com.example.project.admin.dashboard.domain.ProductTypeCount;
import com.example.project.admin.dashboard.domain.SimulationDashboardCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    List<DailySignupCount> selectDailySignupCounts(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    SimulationDashboardCount selectSimulationCounts(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    ConsultationDashboardCount selectConsultationCounts(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    LatestProductDataVersion selectLatestCompletedProductDataVersion();

    ProductTypeCount selectProductTypeCounts(
            @Param("productDataVersionId") Long productDataVersionId
    );

    AdminDashboardErrorCount selectErrorCounts(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    int insertApiErrorLog(
            @Param("httpMethod") String httpMethod,
            @Param("requestUri") String requestUri,
            @Param("responseStatus") int responseStatus,
            @Param("result") String result,
            @Param("elapsedMs") long elapsedMs,
            @Param("occurredAt") LocalDateTime occurredAt
    );
}
