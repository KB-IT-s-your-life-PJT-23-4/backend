package com.example.project.simulation.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.security.JwtProvider;
import com.example.project.security.JwtUtil;
import com.example.project.simulation.dto.request.SimulationExecuteRequest;
import com.example.project.simulation.dto.request.SimulationSaveRequest;
import com.example.project.simulation.dto.response.SimulationResponse;
import com.example.project.simulation.dto.response.SimulationSaveResponse;
import com.example.project.simulation.exception.SimulationError;
import com.example.project.simulation.exception.SimulationException;
import com.example.project.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/gs")
@RequiredArgsConstructor
@Validated
@Log4j2
public class SimulationController {

    private static final String AUTHORIZATION = "Authorization";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final SimulationService simulationService;
    private final JwtProvider jwtProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SimulationResponse> execute(
            @Valid @RequestBody SimulationExecuteRequest request,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        SimulationResponse response = simulationService.execute(request, userId, idempotencyKey);
        log.info("Gift simulation created. simulationId={}, userId={}",
                response.simulationId(), userId);

        return ApiResponse.success(
                HttpStatus.CREATED.value(),
                httpRequest.getRequestURI(),
                response,
                "증여 시뮬레이션이 완료되었습니다."
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
        SimulationResponse response = simulationService.get(parseSimulationId(simulationId), userId);

        return ApiResponse.success(
                HttpStatus.OK.value(),
                httpRequest.getRequestURI(),
                response,
                "증여 시뮬레이션 상세 결과를 조회했습니다."
        );
    }

    @PutMapping("/{simulationId}/save")
    public ApiResponse<SimulationSaveResponse> save(
            @PathVariable String simulationId,
            @Valid @RequestBody SimulationSaveRequest request,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @AuthenticationPrincipal String principal,
            HttpServletRequest httpRequest
    ) {
        Long userId = resolveUserId(principal, authorization);
        SimulationSaveResponse response = simulationService.save(
                parseSimulationId(simulationId),
                request,
                userId,
                idempotencyKey
        );
        log.info("Gift simulation saved. simulationId={}, userId={}",
                response.simulationId(), userId);

        return ApiResponse.success(
                HttpStatus.OK.value(),
                httpRequest.getRequestURI(),
                response,
                "시뮬레이션 현황을 저장했습니다."
        );
    }

    private Long parseSimulationId(String value) {
        try {
            long simulationId = Long.parseLong(value);
            if (simulationId <= 0) {
                throw new NumberFormatException("simulationId must be positive");
            }
            return simulationId;
        } catch (NumberFormatException exception) {
            throw new SimulationException(SimulationError.INVALID_SIMULATION_ID);
        }
    }

    private Long resolveUserId(String principal, String authorization) {
        if (principal != null && !principal.equals("anonymousUser")) {
            try {
                return Long.valueOf(principal);
            } catch (NumberFormatException exception) {
                throw new SimulationException(SimulationError.INVALID_BEARER_TOKEN);
            }
        }

        if (authorization == null || authorization.isBlank()) {
            throw new SimulationException(SimulationError.AUTH_HEADER_MISSING);
        }

        String token = JwtUtil.resolveAccessToken(authorization);
        if (token == null) {
            throw new SimulationException(SimulationError.INVALID_BEARER_TOKEN);
        }
        if (jwtProvider.isExpired(token)) {
            throw new SimulationException(SimulationError.ACCESS_TOKEN_EXPIRED);
        }
        if (!jwtProvider.isValidAccessToken(token)) {
            throw new SimulationException(SimulationError.INVALID_BEARER_TOKEN);
        }

        try {
            return Long.valueOf(jwtProvider.getSubject(token));
        } catch (RuntimeException exception) {
            throw new SimulationException(SimulationError.INVALID_BEARER_TOKEN);
        }
    }
}
