package com.example.project.simulation.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SimulationError {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "유효하지 않은 시뮬레이션 요청입니다."),
    INVALID_AS_OF_DATE(HttpStatus.BAD_REQUEST, "INVALID_AS_OF_DATE", "허용되지 않은 계산 기준일입니다."),
    INVALID_SIMULATION_ID(HttpStatus.BAD_REQUEST, "INVALID_SIMULATION_ID", "시뮬레이션 ID 형식이 올바르지 않습니다."),
    INVALID_SAVE_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_SAVE_REQUEST", "유효하지 않은 저장 요청입니다."),
    ALLOCATION_SUM_MISMATCH(HttpStatus.BAD_REQUEST, "ALLOCATION_SUM_MISMATCH", "상품 배분 합계가 투자 원금과 일치하지 않습니다."),
    ALLOCATION_RATIO_MISMATCH(HttpStatus.BAD_REQUEST, "ALLOCATION_RATIO_MISMATCH", "상품 배분 비율이 투자 성향별 기준과 일치하지 않습니다."),
    PRODUCT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "PRODUCT_LIMIT_EXCEEDED", "선택 상품의 가입 또는 납입 한도를 초과했습니다."),
    PRODUCT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "PRODUCT_TYPE_MISMATCH", "요청 상품 유형과 실제 상품 유형이 일치하지 않습니다."),

    AUTH_HEADER_MISSING(HttpStatus.UNAUTHORIZED, "AUTH_HEADER_MISSING", "Authorization 헤더가 필요합니다."),
    INVALID_BEARER_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_BEARER_TOKEN", "유효하지 않은 Bearer Token입니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_EXPIRED", "Access Token이 만료되었습니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "인증된 사용자를 찾을 수 없습니다."),

    FAMILY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FAMILY_ACCESS_DENIED", "다른 사용자의 수증자에는 접근할 수 없습니다."),
    SIMULATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SIMULATION_ACCESS_DENIED", "다른 사용자의 시뮬레이션에는 접근할 수 없습니다."),

    FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND", "수증자를 찾을 수 없습니다."),
    DEDUCTION_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEDUCTION_RULE_NOT_FOUND", "적용 가능한 증여재산공제 규칙을 찾을 수 없습니다."),
    TAX_BRACKET_NOT_FOUND(HttpStatus.NOT_FOUND, "TAX_BRACKET_NOT_FOUND", "적용 가능한 증여세율표를 찾을 수 없습니다."),
    SIMULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "SIMULATION_NOT_FOUND", "시뮬레이션을 찾을 수 없습니다."),
    SIMULATION_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "SIMULATION_RESULT_NOT_FOUND", "선택한 시뮬레이션 결과를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "선택한 금융상품을 찾을 수 없습니다."),
    SIMULATION_EXPIRED(HttpStatus.NOT_FOUND, "SIMULATION_EXPIRED", "시뮬레이션 보존 기간이 만료되었습니다."),

    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "같은 Idempotency-Key에 다른 요청이 접수되었습니다."),
    PRODUCT_DATA_NOT_READY(HttpStatus.CONFLICT, "PRODUCT_DATA_NOT_READY", "추천에 필요한 상품 데이터 적재가 완료되지 않았습니다."),
    SIMULATION_VERSION_CONFLICT(HttpStatus.CONFLICT, "SIMULATION_VERSION_CONFLICT", "시뮬레이션이 다른 요청에 의해 먼저 변경되었습니다."),
    CALCULATION_VERSION_CONFLICT(HttpStatus.CONFLICT, "CALCULATION_VERSION_CONFLICT", "프론트와 서버의 계산식 버전이 일치하지 않습니다."),
    PRODUCT_SNAPSHOT_MISMATCH(HttpStatus.CONFLICT, "PRODUCT_SNAPSHOT_MISMATCH", "실행 당시 추천하지 않은 상품이 포함되었습니다."),
    SIMULATION_RESULT_INCOMPLETE(HttpStatus.CONFLICT, "SIMULATION_RESULT_INCOMPLETE", "저장된 시나리오 결과 또는 상품 스냅샷이 누락되었습니다."),

    TAX_CALCULATION_NOT_CONVERGED(HttpStatus.INTERNAL_SERVER_ERROR, "TAX_CALCULATION_NOT_CONVERGED", "대납 증여세 계산이 수렴하지 않았습니다."),
    SIMULATION_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_EXECUTION_FAILED", "시뮬레이션 계산 또는 임시 저장에 실패했습니다."),
    SIMULATION_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_READ_FAILED", "시뮬레이션 상세 조회에 실패했습니다."),
    SIMULATION_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_SAVE_FAILED", "시뮬레이션 재계산 또는 저장에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    SimulationError(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
