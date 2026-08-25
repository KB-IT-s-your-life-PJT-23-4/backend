package com.example.project.consultation.reservation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.consultation.reservation.client.KakaoLocalClient;
import com.example.project.consultation.reservation.domain.BranchVO;
import com.example.project.consultation.reservation.dto.response.NearbyBranchResponse;
import com.example.project.consultation.reservation.dto.response.NearbyBranchesResponse;
import com.example.project.consultation.reservation.mapper.TicketMapper;
import com.example.project.consultation.reservation.service.BranchMatchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
@Api(tags = "영업점 검색 API", description = "현재 위치를 기준으로 가까운 KB국민은행 영업점을 조회합니다.")
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final KakaoLocalClient kakaoLocalClient;
    private final BranchMatchService branchMatchService;
    private final TicketMapper ticketMapper;

    private static final long TIMEOUT_MS = 5_000L;
    private static final int NEAREST_OPERATING_LIMIT = 3; // 3개로 제한

    @GetMapping("/nearby")
    @ApiOperation(value = "주변 영업점 조회", notes = "카카오 장소 검색 결과와 운영 영업점 정보를 결합해 가까운 지점을 반환합니다.")
    public DeferredResult<ApiResponse<NearbyBranchesResponse>> nearby(
            @ApiParam(value = "검색어", defaultValue = "국민은행") @RequestParam(defaultValue = "국민은행") String query,
            @ApiParam(value = "현재 위치 경도", required = true, example = "127.0276") @RequestParam double x,
            @ApiParam(value = "현재 위치 위도", required = true, example = "37.4979") @RequestParam double y,
            @ApiParam(value = "검색 반경(미터)", defaultValue = "2000") @RequestParam(defaultValue = "2000") int radius,
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
