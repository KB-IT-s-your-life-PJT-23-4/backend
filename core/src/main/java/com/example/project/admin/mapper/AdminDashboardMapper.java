package com.example.project.admin.mapper;

import com.example.project.admin.domain.DailySignupCount;
import com.example.project.admin.domain.LatestProductDataVersion;
import com.example.project.admin.domain.ProductTypeCount;
import com.example.project.admin.domain.SimulationDashboardCount;
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

    LatestProductDataVersion selectLatestCompletedProductDataVersion();

    ProductTypeCount selectProductTypeCounts(
            @Param("productDataVersionId") Long productDataVersionId
    );
}
