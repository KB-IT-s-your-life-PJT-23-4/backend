package com.example.project.consultation.reservation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.reservation.client.KakaoLocalClient;
import com.example.project.consultation.reservation.domain.BranchVO;
import com.example.project.consultation.reservation.dto.response.NearbyBranchResponse;
import com.example.project.consultation.reservation.dto.response.NearbyBranchesResponse;
import com.example.project.consultation.reservation.mapper.TicketMapper;
import com.example.project.consultation.reservation.service.BranchMatchService;
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
    private static final int NEAREST_OPERATING_LIMIT = 3; // 3개로 제한

    @GetMapping("/nearby")
    public DeferredResult<ApiResponse<NearbyBranchesResponse>> nearby(
            @RequestParam(defaultValue = "국민은행") String query,
            @RequestParam double x,
            @RequestParam double y,
            @RequestParam(defaultValue = "2000") int radius,
            HttpServletRequest httpRequest
    ) {
        DeferredResult<ApiResponse<NearbyBranchesResponse>> deferredResult = new DeferredResult<>(TIMEOUT_MS);

        Mono<List<BranchVO>> branchesMono =
                Mono.fromCallable(ticketMapper::selectAllActiveBranches)
                        .subscribeOn(Schedulers.boundedElastic());

        Mono.zip(kakaoLocalClient.searchKeyword(query, x, y, radius), branchesMono)
                .flatMap(tuple -> {
                    List<BranchVO> branches = tuple.getT2();

                    return branchMatchService.findNearestOperatingWithDetail(x, y, branches, NEAREST_OPERATING_LIMIT)
                            .map(nearestOperating -> {
                                List<NearbyBranchResponse> merged = branchMatchService.merge(tuple.getT1(), branches);
                                List<NearbyBranchResponse> nearbyExcludingShown =
                                        branchMatchService.excludeAlreadyShown(merged, nearestOperating);

                                return new NearbyBranchesResponse(nearestOperating, nearbyExcludingShown);
                            });
                })
                .subscribe(
                        data -> deferredResult.setResult(
                                ApiResponse.success(ResponseCode.SUCCESS, httpRequest.getRequestURI(), data)
                        ),
                        deferredResult::setErrorResult
                );

        return deferredResult;
    }
}