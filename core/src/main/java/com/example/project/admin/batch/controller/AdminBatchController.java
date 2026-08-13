package com.example.project.admin.batch.controller;

import com.example.project.admin.batch.dto.response.BatchJobExecutionPageResponse;
import com.example.project.admin.batch.dto.response.BatchJobResponse;
import com.example.project.admin.batch.service.AdminBatchService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.Pagination;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.security.AdminAccessValidator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import java.util.List;

@ApiLog
@Api(tags = "관리자 배치 작업 관리 API")
@RestController
@Validated
@RequestMapping("/api/admin/batch")
@RequiredArgsConstructor
public class AdminBatchController {

    private final AdminBatchService adminBatchService;
    private final AdminAccessValidator adminAccessValidator;

    @GetMapping("/job")
    public ApiResponse<List<BatchJobResponse>> getJobList(HttpServletRequest request) {
        List<BatchJobResponse> data = adminBatchService.getJobList();

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    @GetMapping("/execution")
    public ApiResponse<BatchJobExecutionPageResponse> getPageExecutionList(
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(Pagination.MAX_PAGE_SIZE)
            int size,

            @RequestParam(required = false)
            String jobName,

            @RequestParam(required = false)
            String status,

            HttpServletRequest request
    ) {
        BatchJobExecutionPageResponse data =
                adminBatchService.getPageExecutionList(page, size, jobName, status);

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    /**
     * 실행은 접수만 하고 바로 돌려준다. 배치는 몇 분씩 걸려서 응답을 기다리면 요청이 끊긴다.
     * 결과는 실행 이력으로 확인하고, 실패하면 관리자 알림에도 쌓인다.
     */
    @ApiOperation(value = "배치 수동 실행", notes = "ROOT 또는 MIDDLE 관리자가 배치를 새 실행으로 띄웁니다.")
    @PostMapping("/job/{jobName}/run")
    public ApiResponse<Void> run(
            @PathVariable String jobName,
            Authentication authentication,
            HttpServletRequest request
    ) {
        adminAccessValidator.requireRootOrMiddle(authentication);
        adminBatchService.run(jobName);

        return ApiResponse.success(ResponseCode.ACCEPTED, request.getRequestURI(), null);
    }

    @ApiOperation(
            value = "배치 실패 지점부터 재시작",
            notes = "실패했거나 중단된 실행을 같은 JobInstance 로 이어서 돌립니다. 성공한 Step 은 건너뜁니다."
    )
    @PostMapping("/execution/{jobExecutionId}/restart")
    public ApiResponse<Void> restart(
            @Positive @PathVariable long jobExecutionId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        adminAccessValidator.requireRootOrMiddle(authentication);
        adminBatchService.restart(jobExecutionId);

        return ApiResponse.success(ResponseCode.ACCEPTED, request.getRequestURI(), null);
    }
}
