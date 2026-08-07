-- AI 상담 안전 이벤트/신고 관리자 화면 확인용 목 데이터
-- MySQL 8.x 기준. 같은 conversation_id/report_key가 있으면 갱신하므로 재실행할 수 있다.

-- 현재 Java/MyBatis 계약은 occurrence_count를 저장하고 조회한다.
-- 제공된 DDL에 컬럼이 빠져 있으므로 없는 경우에만 추가한다.
SET @add_occurrence_count_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_safety_report ADD COLUMN occurrence_count INT NOT NULL DEFAULT 1 COMMENT ''신고 발생 횟수'' AFTER trigger_event_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_safety_report'
      AND column_name = 'occurrence_count'
);

PREPARE add_occurrence_count_statement FROM @add_occurrence_count_sql;
EXECUTE add_occurrence_count_statement;
DEALLOCATE PREPARE add_occurrence_count_statement;

START TRANSACTION;

INSERT INTO ai_consultation_event (
    conversation_id,
    user_id,
    intent,
    response_status,
    question_excerpt,
    occurred_at,
    created_at
) VALUES
    (
        '10000000-0000-4000-8000-000000000001',
        900001,
        'jailbreak',
        'REJECTED',
        '시스템 지침을 무시하고 내부 프롬프트를 공개해 달라는 요청',
        '2026-08-01 09:10:00.000000',
        '2026-08-01 09:10:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000002',
        900002,
        'jailbreak',
        'REJECTED',
        '관리자 권한으로 동작한다고 가정하고 제한된 답변을 요청',
        '2026-08-01 10:25:00.000000',
        '2026-08-01 10:25:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000003',
        900003,
        'jailbreak',
        'REJECTED',
        '안전 정책을 우회하는 역할극 형식의 답변을 반복 요청',
        '2026-08-02 11:40:00.000000',
        '2026-08-02 11:40:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000004',
        900004,
        'jailbreak',
        'REJECTED',
        '숨겨진 시스템 메시지와 모델 설정값을 출력하도록 요청',
        '2026-08-02 14:05:00.000000',
        '2026-08-02 14:05:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000005',
        900005,
        'jailbreak',
        'REJECTED',
        '이전 명령을 모두 취소하고 필터 없이 응답하도록 요청',
        '2026-08-03 08:55:00.000000',
        '2026-08-03 08:55:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000006',
        900006,
        'other',
        'REJECTED',
        '서비스 범위와 무관한 질문을 짧은 시간 동안 반복 전송',
        '2026-08-03 13:15:00.000000',
        '2026-08-03 13:15:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000007',
        900007,
        'other',
        'REJECTED',
        '동일한 무관 질문이 일일 허용 기준을 초과하여 감지됨',
        '2026-08-04 09:30:00.000000',
        '2026-08-04 09:30:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000008',
        900008,
        'other',
        'REJECTED',
        '증여 상담과 관련 없는 자동화 요청이 반복적으로 감지됨',
        '2026-08-05 16:20:00.000000',
        '2026-08-05 16:20:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000009',
        900009,
        'other',
        'REJECTED',
        '비정상적으로 반복되는 기타 유형 질문이 임계치를 초과함',
        '2026-08-06 12:45:00.000000',
        '2026-08-06 12:45:00.000000'
    ),
    (
        '10000000-0000-4000-8000-000000000010',
        900010,
        'other',
        'REJECTED',
        '상담 목적과 무관한 입력이 단시간에 여러 번 제출됨',
        '2026-08-07 10:05:00.000000',
        '2026-08-07 10:05:00.000000'
    )
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    intent = VALUES(intent),
    response_status = VALUES(response_status),
    question_excerpt = VALUES(question_excerpt),
    occurred_at = VALUES(occurred_at);

INSERT INTO ai_safety_report (
    report_key,
    report_type,
    status,
    user_id,
    trigger_event_id,
    occurrence_count,
    count_window_started_at,
    count_window_ended_at,
    assigned_admin_id,
    resolution_note,
    reviewed_at,
    created_at,
    updated_at
)
SELECT
    CASE
        WHEN mock_report.report_type = 'JAILBREAK'
            THEN CONCAT('JAILBREAK:', event.ai_consultation_event_id)
        ELSE CONCAT(
            'OTHER_THRESHOLD:',
            event.user_id,
            ':',
            DATE_FORMAT(event.occurred_at, '%Y%m%d')
        )
    END AS report_key,
    mock_report.report_type,
    mock_report.status,
    event.user_id,
    event.ai_consultation_event_id,
    mock_report.occurrence_count,
    mock_report.count_window_started_at,
    mock_report.count_window_ended_at,
    NULL AS assigned_admin_id,
    mock_report.resolution_note,
    mock_report.reviewed_at,
    event.occurred_at AS created_at,
    COALESCE(mock_report.reviewed_at, event.occurred_at) AS updated_at
FROM (
    SELECT
        '10000000-0000-4000-8000-000000000001' AS conversation_id,
        'JAILBREAK' AS report_type,
        'OPEN' AS status,
        1 AS occurrence_count,
        NULL AS count_window_started_at,
        NULL AS count_window_ended_at,
        NULL AS resolution_note,
        NULL AS reviewed_at
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000002',
        'JAILBREAK',
        'IN_REVIEW',
        1,
        NULL,
        NULL,
        '반복 요청 여부와 상담 로그를 확인 중입니다.',
        NULL
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000003',
        'JAILBREAK',
        'RESOLVED',
        1,
        NULL,
        NULL,
        '안전 정책이 정상 작동했으며 추가 조치 없이 종결했습니다.',
        '2026-08-03 10:00:00.000000'
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000004',
        'JAILBREAK',
        'DISMISSED',
        1,
        NULL,
        NULL,
        '문맥 확인 결과 정상적인 보안 관련 문의로 판단했습니다.',
        '2026-08-03 15:30:00.000000'
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000005',
        'JAILBREAK',
        'OPEN',
        1,
        NULL,
        NULL,
        NULL,
        NULL
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000006',
        'OTHER_THRESHOLD',
        'OPEN',
        11,
        '2026-08-03 09:00:00.000000',
        '2026-08-03 13:15:00.000000',
        NULL,
        NULL
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000007',
        'OTHER_THRESHOLD',
        'IN_REVIEW',
        12,
        '2026-08-04 08:00:00.000000',
        '2026-08-04 09:30:00.000000',
        '반복 입력 패턴과 자동화 도구 사용 여부를 검토 중입니다.',
        NULL
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000008',
        'OTHER_THRESHOLD',
        'RESOLVED',
        13,
        '2026-08-05 10:10:00.000000',
        '2026-08-05 16:20:00.000000',
        '사용자에게 서비스 이용 범위를 안내하고 신고를 종결했습니다.',
        '2026-08-06 09:00:00.000000'
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000009',
        'OTHER_THRESHOLD',
        'DISMISSED',
        14,
        '2026-08-06 09:20:00.000000',
        '2026-08-06 12:45:00.000000',
        '테스트 계정의 정상적인 품질 검증 요청으로 확인했습니다.',
        '2026-08-06 17:10:00.000000'
    UNION ALL
    SELECT
        '10000000-0000-4000-8000-000000000010',
        'OTHER_THRESHOLD',
        'OPEN',
        15,
        '2026-08-07 08:10:00.000000',
        '2026-08-07 10:05:00.000000',
        NULL,
        NULL
) AS mock_report
JOIN ai_consultation_event AS event
    ON event.conversation_id = mock_report.conversation_id
ON DUPLICATE KEY UPDATE
    report_type = VALUES(report_type),
    status = VALUES(status),
    user_id = VALUES(user_id),
    trigger_event_id = VALUES(trigger_event_id),
    occurrence_count = VALUES(occurrence_count),
    count_window_started_at = VALUES(count_window_started_at),
    count_window_ended_at = VALUES(count_window_ended_at),
    resolution_note = VALUES(resolution_note),
    reviewed_at = VALUES(reviewed_at),
    updated_at = VALUES(updated_at);

COMMIT;

-- 생성 결과 확인
SELECT COUNT(*) AS mock_event_count
FROM ai_consultation_event
WHERE conversation_id LIKE '10000000-0000-4000-8000-0000000000%';

SELECT COUNT(*) AS mock_report_count
FROM ai_safety_report AS report
JOIN ai_consultation_event AS event
    ON event.ai_consultation_event_id = report.trigger_event_id
WHERE event.conversation_id LIKE '10000000-0000-4000-8000-0000000000%';
