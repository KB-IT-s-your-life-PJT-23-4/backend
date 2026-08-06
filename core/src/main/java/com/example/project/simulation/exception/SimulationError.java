package com.example.project.simulation.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SimulationError {
    INVALID_SIMULATION_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_SIMULATION_REQUEST", "유효하지 않은 시뮬레이션 요청입니다."),
    INVALID_REQUESTED_AMOUNT(HttpStatus.BAD_REQUEST, "INVALID_REQUESTED_AMOUNT", "증여 예정 금액은 1원 이상이어야 합니다."),
    INVALID_INVESTMENT_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_PERIOD", "운용 기간은 1~240개월이어야 합니다."),
    INVALID_GIFT_DATE(HttpStatus.BAD_REQUEST, "INVALID_GIFT_DATE", "증여 예정일은 오늘 또는 이후 날짜여야 합니다."),
    INVALID_TAX_PAYMENT_METHOD(HttpStatus.BAD_REQUEST, "INVALID_TAX_PAYMENT_METHOD", "지원하지 않는 증여세 납부 방식입니다."),
    INVALID_SIMULATION_ID(HttpStatus.BAD_REQUEST, "INVALID_SIMULATION_ID", "유효하지 않은 시뮬레이션 ID입니다."),
    INVALID_PRODUCT_VERSION_ID(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_VERSION_ID", "유효하지 않은 상품 버전 ID입니다."),
    INVALID_SAVE_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_SAVE_REQUEST", "유효하지 않은 저장 요청입니다."),
    REPLACEMENT_CONFIRMATION_REQUIRED(HttpStatus.BAD_REQUEST, "REPLACEMENT_CONFIRMATION_REQUIRED", "기존 저장 결과 대체 여부가 필요합니다."),
    EXISTING_SAVED_SIMULATION_ID_REQUIRED(HttpStatus.BAD_REQUEST, "EXISTING_SAVED_SIMULATION_ID_REQUIRED", "대체할 기존 저장 결과 ID가 필요합니다."),
    DUPLICATE_PRODUCT_SELECTION(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT_SELECTION", "같은 상품 후보를 중복 선택할 수 없습니다."),
    DUPLICATE_PRODUCT_TYPE(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT_TYPE", "같은 상품 유형에서 여러 상품을 선택할 수 없습니다."),
    INVALID_PREFERENTIAL_CONDITION(HttpStatus.BAD_REQUEST, "INVALID_PREFERENTIAL_CONDITION", "선택할 수 없는 우대금리 조건입니다."),
    ETF_PREFERENTIAL_CONDITION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ETF_PREFERENTIAL_CONDITION_NOT_ALLOWED", "ETF에는 우대금리 조건을 적용할 수 없습니다."),
    INVALID_SIMULATION_STATUS_FILTER(HttpStatus.BAD_REQUEST, "INVALID_SIMULATION_STATUS_FILTER", "유효하지 않은 시뮬레이션 상태입니다."),
    INVALID_FAMILY_ID(HttpStatus.BAD_REQUEST, "INVALID_FAMILY_ID", "유효하지 않은 수증자 ID입니다."),
    INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "유효하지 않은 페이지 요청입니다."),

    AUTH_HEADER_MISSING(HttpStatus.UNAUTHORIZED, "AUTH_HEADER_MISSING", "인증이 필요합니다."),
    INVALID_BEARER_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_BEARER_TOKEN", "유효하지 않은 Bearer Token입니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_EXPIRED", "Access Token이 만료되었습니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "토큰 사용자가 존재하지 않습니다."),

    FAMILY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FAMILY_ACCESS_DENIED", "해당 수증자에 대한 접근 권한이 없습니다."),
    SIMULATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SIMULATION_ACCESS_DENIED", "해당 시뮬레이션에 대한 접근 권한이 없습니다."),

    FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND", "수증자를 찾을 수 없습니다."),
    DEDUCTION_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEDUCTION_RULE_NOT_FOUND", "적용 가능한 증여재산공제 규칙을 찾을 수 없습니다."),
    TAX_BRACKET_NOT_FOUND(HttpStatus.NOT_FOUND, "TAX_BRACKET_NOT_FOUND", "적용 가능한 증여세율표를 찾을 수 없습니다."),
    SIMULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "SIMULATION_NOT_FOUND", "시뮬레이션을 찾을 수 없습니다."),
    SIMULATION_EXPIRED(HttpStatus.NOT_FOUND, "SIMULATION_EXPIRED", "시뮬레이션 보존 기간이 만료되었습니다."),
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", "선택 포트폴리오를 찾을 수 없습니다."),
    SIMULATION_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "SIMULATION_PRODUCT_NOT_FOUND", "선택 상품 후보를 찾을 수 없습니다."),
    PRODUCT_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_VERSION_NOT_FOUND", "상품 상세정보를 찾을 수 없습니다."),
    PRODUCT_NOT_IN_SIMULATION(HttpStatus.NOT_FOUND, "PRODUCT_NOT_IN_SIMULATION", "해당 시뮬레이션에 포함된 상품이 아닙니다."),

    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "같은 Idempotency-Key에 다른 요청이 전달되었습니다."),
    PRODUCT_DATA_NOT_READY(HttpStatus.CONFLICT, "PRODUCT_DATA_NOT_READY", "시뮬레이션에 사용할 상품 데이터가 준비되지 않았습니다."),
    PRODUCT_DATA_VERSION_INCOMPLETE(HttpStatus.CONFLICT, "PRODUCT_DATA_VERSION_INCOMPLETE", "상품 데이터 버전의 필수 정보가 누락되었습니다."),
    PRODUCT_CANDIDATE_NOT_FOUND(HttpStatus.CONFLICT, "PRODUCT_CANDIDATE_NOT_FOUND", "조건을 만족하는 추천 상품을 찾을 수 없습니다."),
    SIMULATION_RESULT_INCOMPLETE(HttpStatus.CONFLICT, "SIMULATION_RESULT_INCOMPLETE", "시뮬레이션 결과 스냅샷이 완전하지 않습니다."),
    SIMULATION_SNAPSHOT_INCOMPLETE(HttpStatus.CONFLICT, "SIMULATION_SNAPSHOT_INCOMPLETE", "시뮬레이션 결과 스냅샷이 완전하지 않습니다."),
    SIMULATION_RECOMMENDATION_INCOMPLETE(HttpStatus.CONFLICT, "SIMULATION_RECOMMENDATION_INCOMPLETE", "투자 성향별 추천 포트폴리오 정보가 완전하지 않습니다."),
    SIMULATION_RETURN_CALCULATION_INVALID(HttpStatus.CONFLICT, "SIMULATION_RETURN_CALCULATION_INVALID", "예상 수익률을 계산할 수 없습니다."),
    SIMULATION_HISTORY_INCOMPLETE(HttpStatus.CONFLICT, "SIMULATION_HISTORY_INCOMPLETE", "저장된 시뮬레이션 선택 정보가 완전하지 않습니다."),
    SAVED_SELECTION_INCOMPLETE(HttpStatus.CONFLICT, "SAVED_SELECTION_INCOMPLETE", "저장된 시뮬레이션 선택 정보가 완전하지 않습니다."),
    SELECTED_PORTFOLIO_MISMATCH(HttpStatus.CONFLICT, "SELECTED_PORTFOLIO_MISMATCH", "선택 포트폴리오가 해당 시뮬레이션에 속하지 않습니다."),
    SIMULATION_VERSION_CONFLICT(HttpStatus.CONFLICT, "SIMULATION_VERSION_CONFLICT", "시뮬레이션이 다른 요청에서 먼저 변경되었습니다."),
    ACTIVE_SAVED_SIMULATION_EXISTS(HttpStatus.CONFLICT, "ACTIVE_SAVED_SIMULATION_EXISTS", "이미 저장된 증여 시뮬레이션이 있습니다. 기존 결과를 대체할지 확인해주세요."),
    EXISTING_SAVED_SIMULATION_CHANGED(HttpStatus.CONFLICT, "EXISTING_SAVED_SIMULATION_CHANGED", "기존 저장 결과가 변경되었습니다. 최신 이력을 확인해주세요."),
    SIMULATION_REPLACEMENT_CONFLICT(HttpStatus.CONFLICT, "SIMULATION_REPLACEMENT_CONFLICT", "동시 요청으로 저장 결과 대체에 실패했습니다."),
    PORTFOLIO_NOT_IN_SIMULATION(HttpStatus.CONFLICT, "PORTFOLIO_NOT_IN_SIMULATION", "다른 시뮬레이션의 포트폴리오입니다."),
    PORTFOLIO_NOT_RECOMMENDED(HttpStatus.CONFLICT, "PORTFOLIO_NOT_RECOMMENDED", "추천되지 않은 포트폴리오는 저장할 수 없습니다."),
    PRODUCT_NOT_IN_PORTFOLIO(HttpStatus.CONFLICT, "PRODUCT_NOT_IN_PORTFOLIO", "선택 포트폴리오의 상품 후보가 아닙니다."),
    PRODUCT_TYPE_SELECTION_INCOMPLETE(HttpStatus.CONFLICT, "PRODUCT_TYPE_SELECTION_INCOMPLETE", "배분된 상품 유형의 선택이 누락되었습니다."),
    PORTFOLIO_ALLOCATION_MISMATCH(HttpStatus.CONFLICT, "PORTFOLIO_ALLOCATION_MISMATCH", "포트폴리오 배분액과 선택 상품 금액이 일치하지 않습니다."),
    PRODUCT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "PRODUCT_LIMIT_EXCEEDED", "선택 상품의 가입 금액·기간·월 납입 한도를 초과했습니다."),
    PRODUCT_DATA_VERSION_MISMATCH(HttpStatus.CONFLICT, "PRODUCT_DATA_VERSION_MISMATCH", "실행 당시 상품 데이터 버전과 일치하지 않습니다."),
    CALCULATION_VERSION_CONFLICT(HttpStatus.CONFLICT, "CALCULATION_VERSION_CONFLICT", "프론트와 서버 계산식 버전이 일치하지 않습니다."),
    PRODUCT_TYPE_DETAIL_MISMATCH(HttpStatus.CONFLICT, "PRODUCT_TYPE_DETAIL_MISMATCH", "상품 유형과 상세정보가 일치하지 않습니다."),
    PRODUCT_DETAIL_INCOMPLETE(HttpStatus.CONFLICT, "PRODUCT_DETAIL_INCOMPLETE", "상품 상세 스냅샷이 완전하지 않습니다."),

    DONOR_PAYMENT_CALCULATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DONOR_PAYMENT_CALCULATION_FAILED", "증여세 대납 계산에 실패했습니다."),
    TAX_CALCULATION_NOT_CONVERGED(HttpStatus.INTERNAL_SERVER_ERROR, "DONOR_PAYMENT_CALCULATION_FAILED", "증여세 대납 계산에 실패했습니다."),
    SIMULATION_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_EXECUTION_FAILED", "증여 시뮬레이션 처리 중 오류가 발생했습니다."),
    SIMULATION_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_READ_FAILED", "시뮬레이션 상세 조회 중 오류가 발생했습니다."),
    PRODUCT_DETAIL_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_DETAIL_READ_FAILED", "상품 상세정보 조회 중 오류가 발생했습니다."),
    SIMULATION_HISTORY_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_HISTORY_READ_FAILED", "시뮬레이션 이력 조회 중 오류가 발생했습니다."),
    PREVIOUS_SIMULATION_RESET_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PREVIOUS_SIMULATION_RESET_FAILED", "기존 저장 결과 초기화에 실패했습니다."),
    SIMULATION_RECALCULATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_RECALCULATION_FAILED", "서버 최종 계산에 실패했습니다."),
    SIMULATION_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATION_SAVE_FAILED", "시뮬레이션 최종 저장 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    SimulationError(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
