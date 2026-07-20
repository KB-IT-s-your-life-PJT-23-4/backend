package com.example.project.common.api;

import lombok.Getter;

@Getter
public enum ResponseCode {

    // 100번대 : 요청 및 처리 상태
    REQUEST_RECEIVED(100, "REQUEST_RECEIVED", "요청이 정상적으로 접수되었습니다"),
    PROCESSING(101, "PROCESSING", "요청을 처리중입니다"),
    WAITING(102, "WAITING", "처리를 위해 대기중입니다"),
    VERIFICATION_REQUIRED(103, "VERIFICATION_REQUIRED", "추가 인증/확인이 필요합니다"),
    ADDITIONAL_INFO_REQUIRED(104, "ADDITIONAL_INFO_REQUIRED", "추가 정보 입력이 필요합니다"),
    BATCH_PROCESSING(105, "BATCH_PROCESSING", "배치 작업이 실행 중입니다"),
    AI_RESPONSE_GENERATING(106, "AI_RESPONSE_GENERATING", "AI가 답변을 생성 중입니다"),
    EXTERNAL_API_WAITING(107, "EXTERNAL_API_WAITING", "외부 API 응답을 기다리는 중입니다"),

    // 200번대 : 성공
    SUCCESS(200, "SUCCESS", "요청이 정상적으로 처리되었습니다"),
    CREATED(201, "CREATED", "데이터가 정상적으로 생성되었습니다"),
    ACCEPTED(202, "ACCEPTED", "요청이 접수되었습니다"),
    UPDATED(203, "UPDATED", "데이터 정상적으로 수정되었습니다"),
    DELETED(204, "DELETED", "데이터가 정상적으로 삭제되었습니다"),
    LOGIN_SUCCESS(205, "LOGIN_SUCCESS", "정상적으로 로그인 되었습니다"),
    LOGOUT_SUCCESS(206, "LOGOUT_SUCCESS", "정상적으로 로그아웃 되었습니다"),
    SIMULATION_SUCCESS(207, "SIMULATION_SUCCESS", "증여 시뮬레이션 계산에 성공하였습니다"),
    AI_RESPONSE_SUCCESS(208, "AI_RESPONSE_SUCCESS", "AI 상담 답변 생성에 성공하였습니다"),
    BATCH_SUCCESS(209, "BATCH_SUCCESS", "배치 작업이 정상적으로 완료되었습니다"),

    // 300번대 : 업무 진행 상태
    DRAFT(300, "DRAFT", "작성 중이거나 임시 저장된 상태"),
    PLANNED(301, "PLANNED", "증여가 예정된 상태"),
    PENDING(302, "PENDING", "처리 또는 승인을 기다리는 상태"),
    IN_PROGRESS(303, "IN_PROGRESS", "증여 절차가 진행 중인 상태"),
    DOCUMENT_REQUIRED(304, "DOCUMENT_REQUIRED", "신고 서류 제출이 필요한 상태"),
    REPORT_PENDING(305, "REPORT_PENDING", "증여세 신고 대기 상태"),
    REPORTED(306, "REPORTED", "증여세 신고가 완료된 상태"),
    COMPLETED(307, "COMPLETED", "증여 절차가 최종 완료된 상태"),
    CANCELED(308, "CANCELED", "사용자가 증여 계획을 취소한 상태"),
    EXPIRED(309, "EXPIRED", "증여 계획 또는 요청의 유효기간이 만료된 상태"),
    REMINDER_SCHEDULED(310, "REMINDER_SCHEDULED", "리마인더가 등록된 상태"),
    DEDUCTION_RENEWAL_PENDING(311, "DEDUCTION_RENEWAL_PENDING", "공제 한도 갱신일을 기다리는 상태"),
    DEDUCTION_RENEWED(312, "DEDUCTION_RENEWED", "10년 합산기간 경과로 공제 한도가 갱신된 상태"),

    // 400번대: 사용자 및 비즈니스 오류
    BAD_REQUEST(400, "BAD_REQUEST", "요청 형식/입력값이 올바르지 않습니다"),
    UNAUTHORIZED(401, "UNAUTHORIZED", "인증 정보가 없거나 유효하지 않습니다"),
    TOKEN_EXPIRED(402, "TOKEN_EXPIRED", "인증 토큰이 만료되었습니다"),
    FORBIDDEN(403, "FORBIDDEN", "해당 기능에 접근할 권한이 없습니다"),
    RESOURCE_NOT_FOUND(404, "RESOURCE_NOT_FOUND", "요청한 데이터를 찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(405, "METHOD_NOT_ALLOWED", "허용되지 않은 HTTP 메서드 요청입니다"),
    DUPLICATE_DATA(406, "DUPLICATE_DATA", "이미 존재하는 데이터입니다"),
    VALIDATION_FAILED(407, "VALIDATION_FAILED", "입력값 검증에 실패하였습니다"),
    REQUEST_TIMEOUT(408, "REQUEST_TIMEOUT", "요청 처리 시간이 초과되었습니다"),
    CONFLICT(409, "CONFLICT", "현재 데이터 상태와 요청이 충돌하였습니다"),
    MEMBER_NOT_FOUND(410, "MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다"),
    BENEFICIARY_NOT_FOUND(411, "BENEFICIARY_NOT_FOUND", "수증자 정보를 찾을 수 없습니다"),
    GIFT_HISTORY_NOT_FOUND(412, "GIFT_HISTORY_NOT_FOUND", "증여 이력을 찾을 수 없습니다"),
    INVALID_GIFT_AMOUNT(413, "INVALID_GIFT_AMOUNT", "증여 금액이 올바르지 않습니다"),
    SIMULATION_INPUT_INCOMPLETE(414, "SIMULATION_INPUT_INCOMPLETE", "시뮬레이션 입력 정보가 부족합니다"),
    PRODUCT_NOT_FOUND(415, "PRODUCT_NOT_FOUND", "금융상품 정보를 찾을 수 없습니다"),
    AI_QUESTION_INCOMPLETE(416, "AI_QUESTION_INCOMPLETE", "AI 상담 판단에 필요한 정보가 부족합니다"),
    UNSUPPORTED_QUESTION(417, "UNSUPPORTED_QUESTION", "지원하지 않는 상담 분야의 질문입니다"),
    EXPERT_REVIEW_REQUIRED(418, "EXPERT_REVIEW_REQUIRED", "전문가의 추가 검토가 필요한 사례입니다"),
    FILE_FORMAT_INVALID(419, "FILE_FORMAT_INVALID", "지원하지 않는 파일 형식입니다"),
    FILE_SIZE_EXCEEDED(420, "FILE_SIZE_EXCEEDED", "첨부파일 허용 용량을 초과합니다"),

    // 500번대: 서버 및 외부 시스템 오류
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 내부에서 알 수 없는 오류가 발생하였습니다"),
    DATABASE_ERROR(501, "DATABASE_ERROR", "데이터베이스 처리 중 오류가 발생하였습니다"),
    EXTERNAL_API_ERROR(502, "EXTERNAL_API_ERROR", "외부 API 호출 중 오류가 발생하였습니다"),
    SERVICE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "서버 또는 외부 서비스가 일시적으로 사용 불가능합니다"),
    EXTERNAL_API_TIMEOUT(504, "EXTERNAL_API_TIMEOUT", "외부 API 응답 시간이 초과되었습니다"),
    FASTAPI_SERVER_ERROR(505, "FASTAPI_SERVER_ERROR", "FastAPI 서버 처리 중 오류가 발생하였습니다"),
    OPENAI_API_ERROR(506, "OPENAI_API_ERROR", "OpenAI API 호출 중 오류가 발생하였습니다"),
    VECTOR_DB_ERROR(507, "VECTOR_DB_ERROR", "벡터 데이터베이스 처리 중 오류가 발생하였습니다"),
    EMBEDDING_ERROR(508, "EMBEDDING_ERROR", "임베딩 생성 중 오류가 발생하였습니다"),
    RAG_SEARCH_ERROR(509, "RAG_SEARCH_ERROR", "관련 법령 및 FAQ 검색 중 오류가 발생하였습니다"),
    BATCH_PROCESSING_ERROR(510, "BATCH_PROCESSING_ERROR", "Spring Batch 실행 중 오류가 발생하였습니다"),
    LAW_API_ERROR(511, "LAW_API_ERROR", "국가법령정보 API 호출에 실패하였습니다"),
    PRODUCT_API_ERROR(512, "PRODUCT_API_ERROR", "금융상품 API 호출에 실패하였습니다"),
    FILE_PROCESSING_ERROR(513, "FILE_PROCESSING_ERROR", "파일 생성 또는 다운로드 중 오류가 발생하였습니다"),
    NOTIFICATION_ERROR(514, "NOTIFICATION_ERROR", "리마인더 또는 알림 전송에 실패하였습니다"),
    DATA_SYNCHRONIZATION_ERROR(515, "DATA_SYNCHRONIZATION_ERROR", "외부 데이터 최신화 중 오류가 발생하였습니다");

    private final int code;
    private final String status;
    private final String message;

    ResponseCode(int code, String status, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
