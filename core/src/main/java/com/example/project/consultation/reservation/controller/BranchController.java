package com.example.project.consultation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.client.KakaoLocalClient;
import com.example.project.consultation.dto.response.NearbyBranchResponse;
import com.example.project.consultation.mapper.TicketMapper;
import com.example.project.consultation.service.BranchMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final KakaoLocalClient kakaoLocalClient;
    private final BranchMatchService branchMatchService;
    private final TicketMapper ticketMapper;

    private static final long TIMEOUT_MS = 5_000L;

    @GetMapping("/nearby")
    public DeferredResult<ApiResponse<List<NearbyBranchResponse>>> nearby(
            @RequestParam(defaultValue = "국민은행") String query,
            @RequestParam double x,
            @RequestParam double y,
            @RequestParam(defaultValue = "2000") int radius,
            HttpServletRequest httpRequest
    ) {
        DeferredResult<ApiResponse<List<NearbyBranchResponse>>> deferredResult = new DeferredResult<>(TIMEOUT_MS);

        Mono<List<com.example.project.consultation.domain.BranchVO>> branchesMono =
                Mono.fromCallable(ticketMapper::selectAllActiveBranches)
                        .subscribeOn(Schedulers.boundedElastic());

        Mono.zip(kakaoLocalClient.searchKeyword(query, x, y, radius), branchesMono)
                .map(tuple -> branchMatchService.merge(tuple.getT1(), tuple.getT2()))
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data)
                        ),
                        deferredResult::setErrorResult
                );

        return deferredResult;
    }
}