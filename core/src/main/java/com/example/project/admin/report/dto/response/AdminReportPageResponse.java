package com.example.project.admin.report.dto.response;

import com.example.project.consultation.domain.AiSafetyReportVO;
import com.example.project.simulation.dto.response.SimulationHistoryResponse;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class AdminReportPageResponse {
    private final List<AiSafetyReportVO> reports;
    private final;


    @Getter
    @AllArgsConstructor
    public static class Pagination {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final int numberOfElements;
        private final boolean first;
        private final boolean last;
        private final boolean hasNext;
        private final boolean hasPrevious;
    }
}
