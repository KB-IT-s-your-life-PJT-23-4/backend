package com.example.project.simulation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.logging.ApiLog;
import com.example.project.security.JwtProvider;
import com.example.project.security.JwtUtil;
import com.example.project.simulation.dto.request.SimulationExecuteRequest;
import com.example.project.simulation.dto.request.SimulationSaveRequest;
import com.example.project.simulation.dto.request.CustomPortfolioRequest;
import com.example.project.simulation.dto.response.CustomPortfolioResponse;
import com.example.project.simulation.dto.response.ProductDetailResponse;
import com.example.project.simulation.dto.response.SimulationHistoryResponse;
import com.example.project.simulation.dto.response.SimulationResponse;
import com.example.project.simulation.dto.response.SimulationSaveResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.service.SimulationHistoryService;
import com.example.project.simulation.service.SimulationProductService;
import com.example.project.simulation.service.SimulationService;
import com.example.project.user.service.AccountAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@ApiLog
@RestController
@RequestMapping("/api/gs")
@RequiredArgsConstructor
@Validated
@Log4j2
public class SimulationController {

    private static final String AUTHORIZATION = "Authorization";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final SimulationService simulationService;
    private final SimulationProductService simulationProductService;
    private final SimulationHistoryService simulationHistoryService;
    private final JwtProvider jwtProvider;
    private final AccountAccessService accountAccessService;

    @PostMapping({"", "/"})
    public ResponseEntity<ApiResponse<SimulationResponse>> execute(
            @Valid @RequestBody SimulationExecuteRequest request,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        accountAccessService.requireRestrictedFeatureAccess(userId);
        SimulationResponse response =
                simulationService.execute(request, userId, idempotencyKey);
        URI location = UriComponentsBuilder.fromPath("/api/gs/{id}")
                .buildAndExpand(response.simulationId())
                .toUri();
        log.info(
                "Gift simulation created. simulationId={}, userId={}",
                response.simulationId(),
                userId
        );
        return ResponseEntity.created(location).body(ApiResponse.success(
                HttpStatus.CREATED.value(),
                httpRequest.getRequestURI(),
                response,
                "증여 시뮬레이션이 완료되었습니다."
        ));
    }

    @GetMapping
    public ApiResponse<SimulationHistoryResponse> history(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        SimulationHistoryResponse response = simulationHistoryService.getHistory(
                userId,
                status,
                familyId,
                page,
                size
        );
        String message = response.items().isEmpty()
                ? "조회된 증여 시뮬레이션 이력이 없습니다."
                : "증여 시뮬레이션 이력을 조회했습니다.";
        return ApiResponse.success(
                HttpStatus.OK.value(),
                requestPath(httpRequest),
                response,
                message
        );
    }

    @GetMapping("/{simulationId}")
    public ApiResponse<SimulationResponse> get(
            @PathVariable String simulationId,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        accountAccessService.requireRestrictedFeatureAccess(userId);
        SimulationResponse response = simulationService.get(
                parsePositiveId(simulationId, SimulationError.INVALID_SIMULATION_ID),
                userId
        );
        String message = response.status()
                == com.example.project.simulation.domain.SimulationStatus.SAVED
                ? "저장된 증여 시뮬레이션 상세 결과를 조회했습니다."
                : "증여 시뮬레이션 상세 결과를 조회했습니다.";
        return ApiResponse.success(
                HttpStatus.OK.value(),
                httpRequest.getRequestURI(),
                response,
                message
        );
    }

    @GetMapping("/{simulationId}/products/{kbProductVersionId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> productDetail(
            @PathVariable String simulationId,
            @PathVariable String kbProductVersionId,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
            String ifNoneMatch,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        accountAccessService.requireRestrictedFeatureAccess(userId);
        Long parsedSimulationId = parsePositiveId(
                simulationId,
                SimulationError.INVALID_SIMULATION_ID
        );
        Long parsedProductVersionId = parsePositiveId(
                kbProductVersionId,
                SimulationError.INVALID_PRODUCT_VERSION_ID
        );
        ProductDetailResponse response = simulationProductService.getDetail(
                parsedSimulationId,
                parsedProductVersionId,
                userId
        );
        String etag = "\"kb-product-version-" + parsedProductVersionId + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                    .build();
        }
        String message = switch (response.product().productType()) {
            case DEPOSIT -> "예금 상품 상세정보를 조회했습니다.";
            case SAVINGS -> "적금 상품 상세정보를 조회했습니다.";
            case ETF -> "ETF 상품 상세정보를 조회했습니다.";
        };
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(ApiResponse.success(
                        HttpStatus.OK.value(),
                        httpRequest.getRequestURI(),
                        response,
                        message
                ));
    }

    @PutMapping("/{simulationId}/portfolios/custom")
    public ApiResponse<CustomPortfolioResponse> customizePortfolio(
            @PathVariable String simulationId,
            @Valid @RequestBody CustomPortfolioRequest request,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        accountAccessService.requireRestrictedFeatureAccess(userId);
        CustomPortfolioResponse response = simulationService.customizePortfolio(
                parsePositiveId(simulationId, SimulationError.INVALID_SIMULATION_ID),
                request,
                userId
        );
        return ApiResponse.success(
                HttpStatus.OK.value(),
                httpRequest.getRequestURI(),
                response,
                "커스텀 포트폴리오 비율을 적용했습니다."
        );
    }

    @PatchMapping("/{simulationId}/save")
    public ApiResponse<SimulationSaveResponse> save(
            @PathVariable String simulationId,
            @Valid @RequestBody SimulationSaveRequest request,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        accountAccessService.requireRestrictedFeatureAccess(userId);
        SimulationSaveResponse response = simulationService.save(
                parsePositiveId(simulationId, SimulationError.INVALID_SIMULATION_ID),
                request,
                userId,
                idempotencyKey
        );
        log.info(
                "Gift simulation saved. simulationId={}, userId={}, replaced={}",
                response.simulationId(),
                userId,
                response.replacement().replaced()
        );
        String message = response.replacement().replaced()
                ? "새 시뮬레이션을 최종 저장하고 기존 저장 결과를 임시 이력으로 전환했습니다."
                : "증여 시뮬레이션을 최종 저장했습니다.";
        return ApiResponse.success(
                HttpStatus.OK.value(),
                httpRequest.getRequestURI(),
                response,
                message
        );
    }

    private Long parsePositiveId(String value, SimulationError error) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("ID must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new SimulationException(error);
        }
    }

    private Long resolveUserId(String principal, String authorization) {
        if (principal != null && !principal.equals("anonymousUser")) {
            try {
                return Long.valueOf(principal);
            } catch (NumberFormatException exception) {
                throw new SimulationException(
                        SimulationError.INVALID_BEARER_TOKEN);
            }
        }
        if (authorization == null || authorization.isBlank()) {
            throw new SimulationException(SimulationError.AUTH_HEADER_MISSING);
        }
        String token = JwtUtil.resolveAccessToken(authorization);
        if (token == null) {
            throw new SimulationException(
                    SimulationError.INVALID_BEARER_TOKEN);
        }
        if (jwtProvider.isExpired(token)) {
            throw new SimulationException(SimulationError.ACCESS_TOKEN_EXPIRED);
        }
        if (!jwtProvider.isValidAccessToken(token)) {
            throw new SimulationException(
                    SimulationError.INVALID_BEARER_TOKEN);
        }
        try {
            return Long.valueOf(jwtProvider.getSubject(token));
        } catch (RuntimeException exception) {
            throw new SimulationException(
                    SimulationError.INVALID_BEARER_TOKEN);
        }
    }

    private String requestPath(HttpServletRequest request) {
        return request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
    }
}
