# AI 상담 신규 대화 생성 데드락

## 문제 상황

서로 다른 사용자가 AI 상담을 동시에 처음 시작할 때 일부 요청이 실패했다.

- 실패 시점: `ConversationPersistenceService.createConversation()`
- 실패 SQL: `INSERT INTO ai_conversation`
- Spring 예외: `DeadlockLoserDataAccessException`
- MySQL 예외: `MySQLTransactionRollbackException: Deadlock found when trying to get lock`
- 클라이언트 증상: JSON 대신 HTML 오류 페이지를 HTTP 200으로 받아 JSON 파싱 실패

가족 정보 조회와 공제 한도 조회는 정상적으로 완료됐으므로 가족 데이터 누락이 원인은 아니었다.

## 재현 조건

다음 조건에서 신규 상담 요청을 동시에 실행하면 재현될 수 있었다.

1. 여러 사용자가 각각 유효한 계정과 가족 정보를 보유한다.
2. 해당 사용자들의 `ACTIVE` 상태 대화가 아직 없다.
3. 각 요청이 활성 대화를 `FOR UPDATE`로 조회한다.
4. 조회 결과가 없으면 `ai_conversation`에 신규 행을 추가한다.

문제가 발생한 기존 흐름은 다음과 같았다.

```text
user 행 SELECT ... FOR UPDATE
→ ai_conversation 활성 대화 SELECT ... FOR UPDATE
→ 활성 대화 없음
→ ai_conversation INSERT
```

## 원인

기존 활성 대화 조회는 다음 조건에 `FOR UPDATE`를 사용했다.

```sql
SELECT ...
FROM ai_conversation
WHERE user_id = ?
  AND status = 'ACTIVE'
LIMIT 1
FOR UPDATE;
```

대화 행이 존재하면 해당 레코드를 잠글 수 있지만, 행이 존재하지 않으면 InnoDB의 `REPEATABLE READ` 격리 수준에서 인덱스 갭이 잠길 수 있다.

당시 `ai_conversation`에는 `(user_id, created_at)` 인덱스가 있었고, 신규 사용자 6과 7의 다음 인덱스 레코드는 사용자 8이었다. 두 트랜잭션은 모두 사용자 8 레코드 앞의 동일한 갭을 잠갔다.

InnoDB 데드락 보고서의 핵심은 다음과 같았다.

```text
트랜잭션 1: user_id=7 INSERT
트랜잭션 2: user_id=6 INSERT

index idx_ai_conversation_user_created
lock_mode X locks gap before rec
lock_mode X locks gap before rec insert intention waiting

WE ROLL BACK TRANSACTION (2)
```

두 트랜잭션이 동일한 갭 잠금을 보유한 상태에서 그 구간에 대한 삽입 의도 잠금을 기다리면서 데드락이 형성됐다. MySQL은 사용자 6의 트랜잭션을 희생 대상으로 선택해 롤백했다.

이 문제는 이전 트랜잭션의 `unlock` 누락이 아니다. Spring의 `@Transactional` 트랜잭션은 커밋 또는 롤백 시 InnoDB 잠금을 자동으로 해제한다. 데드락 희생 트랜잭션의 잠금도 MySQL이 롤백하면서 해제한다.

## 해결 방법

서비스는 활성 대화를 조회하기 전에 사용자 행을 이미 잠근다.

```sql
SELECT user_id
FROM `user`
WHERE user_id = ?
FOR UPDATE;
```

이 잠금은 동일한 사용자의 상담 시작 요청을 직렬화한다. 따라서 활성 대화 조회에 다시 `FOR UPDATE`를 사용하지 않고 일반 조회로 변경했다.

변경된 흐름은 다음과 같다.

```text
user 행 SELECT ... FOR UPDATE
→ ai_conversation 활성 대화 일반 SELECT
→ 활성 대화 없음
→ ai_conversation INSERT
```

적용 내용은 다음과 같다.

- `ConversationPersistenceService.prepareTurn()`에서 사용자 행 잠금을 먼저 유지한다.
- 활성 대화 조회는 `selectActiveConversation()`을 사용한다.
- `selectActiveConversationForUpdate()` Mapper 메서드와 XML 구문을 제거한다.
- 내부 대화 ID로 기존 행을 변경하는 완료·실패 흐름의 `selectConversationForUpdate()`는 유지한다.

이 해결 방법의 전제는 대화 생성과 질문 시작의 모든 진입점이 동일한 트랜잭션에서 `lockUser(userId)`를 먼저 호출하는 것이다. 새로운 진입점을 추가할 때도 이 순서를 지켜야 한다.

## 인덱스 검토

활성 대화 조회 성능을 위해 다음 인덱스를 별도로 검토할 수 있다.

```sql
ALTER TABLE ai_conversation
    ADD INDEX idx_ai_conversation_user_status (
        user_id,
        status
    );
```

인덱스 추가는 실행 계획 개선을 위한 별도 변경이다. 존재하지 않는 행을 `FOR UPDATE`로 조회하는 구조를 그대로 유지한 채 인덱스만 추가하는 것은 갭 잠금 데드락을 완전히 방지하는 해결책이 아니다.

사용자별 활성 대화를 하나로 제한하는 `active_user_id` 유니크 인덱스는 최종 무결성 방어선이므로 유지한다.

## 확인 방법

### Mapper XML 검증

```powershell
.\gradlew.bat :core:test --tests "com.example.project.consultation.mapper.ConsultationMapperXmlTest"
```

검증 항목은 다음과 같다.

- 사용자 행 조회에는 `FOR UPDATE`가 존재한다.
- 활성 대화 조회에는 `FOR UPDATE`가 존재하지 않는다.

### 부하 테스트 검증

기존 활성 대화가 없는 서로 다른 테스트 사용자 5명으로 상담 요청을 동시에 한 번씩 실행한다.

```text
Agent: 1
Processes: 1
Threads: 5
Run Count: 1
```

검증 결과에서 다음 항목을 확인한다.

- 사용자별 로그인 계정과 `userId`가 서로 다르다.
- 신규 대화 INSERT가 모두 성공한다.
- `DeadlockLoserDataAccessException`이 발생하지 않는다.
- 모든 상담 응답의 `Content-Type`이 `application/json`이다.

### MySQL 확인

최근 데드락은 다음 명령으로 확인한다.

```sql
SHOW ENGINE INNODB STATUS;
```

부하 테스트 이후 `LATEST DETECTED DEADLOCK`의 시각이 새 테스트 시각으로 갱신되지 않아야 한다.

## 추가 개선 사항

데드락과 같은 데이터 접근 예외가 처리되지 않은 채 JSP 오류 페이지와 HTTP 200으로 반환되지 않도록 공통 예외 응답을 점검해야 한다. API 경로의 오류는 JSON 형식과 적절한 HTTP 상태로 반환해야 한다.

운영 환경에서는 잠금 구조를 개선한 뒤에도 짧은 DB 트랜잭션에 한해 제한적인 데드락 재시도를 고려할 수 있다. FastAPI 호출까지 포함해 재시도하면 외부 요청이 중복될 수 있으므로 재시도 범위는 대화 준비 DB 트랜잭션으로 제한해야 한다.