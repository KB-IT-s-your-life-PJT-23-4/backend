
현재 서비스 AGENTS.md 파일은 어떻게 작성하면 될까 전체 파일 구조랑 이런거 다 읽어서 작성해줘 알려줘 너가 작성하지 말고

(일요일) 오후 3:42

7m 27s 동안 작업
현재 저장소에는 아무 파일도 수정하지 않았습니다. 루트 AGENTS.md는 빈 상태 그대로입니다.

이 프로젝트는 README보다 실제 코드가 더 앞서 있거나 서로 다른 부분이 있어서, AGENTS.md에는 다음 원칙이 특히 중요합니다.

Spring Boot가 아닌 Spring Legacy WAR 프로젝트라는 점
core, batch 멀티모듈 구조
실제 API 경로를 임의로 통일하지 않는 것
ApiResponse.statusCode와 HTTP 상태가 다를 수 있다는 점
Mapper 변경 시 XML과 테스트 Fake Mapper를 함께 수정하는 것
로컬 설정 파일과 비밀정보를 커밋하지 않는 것
전체 테스트의 현재 기준 실패를 통과했다고 보고하지 않는 것
아래 내용을 그대로 AGENTS.md 후보로 권장합니다. 에이전트가 해석하기 쉬우므로 본문은 영어로 작성하는 편이 좋습니다.

# AGENTS.md

## Scope

- These instructions apply to the entire backend repository.
- Follow more specific `AGENTS.md` files if they are added under a subdirectory.
- Read the relevant implementation, configuration, mapper XML, and tests before changing behavior.
- Do not modify files unrelated to the requested task.
- Preserve existing user changes and do not overwrite unfinished work.
- Do not modify this file unless the user explicitly requests it.

## Project Overview

- This repository contains the backend for `MiriZoom`, a gift-planning and gift-tax simulation service.
- It is a Java 17, Spring Framework 5.3 legacy application.
- It is not a Spring Boot project.
- The repository is a Gradle multi-module project:
    - `core`: Spring MVC REST API packaged as `project-core.war`
    - `batch`: Spring Batch command-line application depending on `core`
- Persistence uses MyBatis, MySQL, HikariCP, and JDBC transactions.
- Authentication uses stateless JWT processing through Spring Security.
- Tests use JUnit 5 and Spring Test.

## Source of Truth

- Treat current code, Gradle configuration, mapper XML, and tests as the primary source of truth.
- The README contains useful architectural guidance, but some paths, examples, planned features, and setup instructions may not match the current implementation.
- Do not implement a feature solely because it appears in the README or a TODO.
- When behavior changes intentionally, update the relevant documentation and tests in the same change.
- Preserve UTF-8 encoding. Some existing Korean text may appear corrupted because of historical encoding issues; do not rewrite unrelated text just to normalize encoding.

## Environment

- Use Java 17.
- Use the checked-in Gradle Wrapper instead of a system Gradle installation.
- On Windows, run Gradle with `.\gradlew.bat`.
- On macOS or Linux, run Gradle with `./gradlew`.
- Local runtime configuration is created from:
    - `core/src/main/resources/application.properties.example`
    - `core/src/main/resources/database.properties.example`
    - `batch/src/main/resources/database.properties.example`
- Required local copies include:
    - `core/src/main/resources/application.properties`
    - `core/src/main/resources/database.properties`
    - `batch/src/main/resources/database.properties`
- Never commit credentials, JWT secrets, tokens, or local database settings.
- Files ignored as local secrets must remain untracked.
- Do not print secret values in logs, test output, or final summaries.
- Do not introduce Spring Boot YAML configuration unless the project is intentionally being migrated to Spring Boot.

## Commands

### Windows

```powershell
# Compile production sources
.\gradlew.bat :core:compileJava :batch:compileJava

# Run all tests
.\gradlew.bat test

# Run core tests
.\gradlew.bat :core:test

# Run one test class
.\gradlew.bat :core:test --tests "com.example.project.package.ClassName"

# Build all modules
.\gradlew.bat build

# Build without tests only when explicitly needed
.\gradlew.bat build -x test

# Build the core WAR
.\gradlew.bat :core:war

# Run the batch application
.\gradlew.bat :batch:run
macOS and Linux
Use the same tasks with ./gradlew instead of .\gradlew.bat.

Project Structure
build.gradle: shared versions and Java/test configuration
settings.gradle: registers the core and batch modules
core/build.gradle: Spring MVC WAR dependencies and core test dependencies
batch/build.gradle: Spring Batch application and its dependency on core
core/src/main/java/com/example/project/: application source
core/src/main/resources/mapper/: MyBatis mapper XML files
core/src/main/resources/: MyBatis, Log4j2, and local runtime properties
core/src/main/webapp/WEB-INF/views/: JSP error and index views
core/src/test/java/: unit, MVC, XML parsing, and database integration tests
batch/src/main/java/com/example/project/batch/: batch configuration and jobs
batch/src/main/resources/mapper/: batch MyBatis mapper XML files
.github/: issue, pull request, Jira, and workflow configuration
.husky/: test and commit-message hooks
Core Module Architecture
Organize code by business domain, then by layer.

Current domains include:

auth: login, refresh, and logout
user: signup and user profile management
recipient: gift-recipient and family management
gift: gift history, deductions, filing information, and status changes
simulation: gift and tax scenario calculation and persistence
consultation: FAQ and consultation endpoints
product: product-related shared domain space
security: JWT and Spring Security infrastructure
common: API responses, exceptions, and web utilities
config: Spring MVC and root application configuration
Within a domain, use existing directories where applicable:

controller: HTTP request and response handling
service: business rules and transaction boundaries
mapper: MyBatis mapper interfaces
domain: persistence and business objects
dto/request: inbound API models
dto/response: outbound API models
exception: domain-specific exceptions
Do not introduce a new architectural pattern such as generic repositories, mandatory service interfaces, or a shared base controller unless the task explicitly requires it.

Spring Legacy Rules
Configuration is Java-based through RootConfig, ServletConfig, WebConfig, and SecurityConfig.
WebConfig initializes the application through AbstractAnnotationConfigDispatcherServletInitializer.
core is deployed as a WAR to a Servlet 4 compatible container such as Tomcat.
Do not add a Spring Boot application class, embedded server dependency, or Boot auto-configuration without an explicit migration decision.
Root application components and MVC controller components are scanned separately. Preserve this separation when adding configuration.
Use constructor injection. Lombok @RequiredArgsConstructor is acceptable where it matches nearby code.
Keep HTTP concerns in controllers and business rules in services.
Keep SQL and database-access behavior in MyBatis mappers.
API Contract Rules
Return API payloads using ApiResponse<T> unless an existing endpoint deliberately uses a different response.
Include the request URI in ApiResponse using HttpServletRequest#getRequestURI().
Reuse ResponseCode where an appropriate code already exists.
Do not create duplicate response codes with equivalent meanings.
Use ServiceException for common business failures handled by CommonExceptionAdvice.
Use the simulation-specific exception types for simulation behavior where that convention is already established.
Add Bean Validation annotations to request DTOs or controller parameters when validating input.
Use @Valid or @Validated so the validation rules are actually applied.
Do not expose persistence objects directly when an existing response DTO is available.
Do not include passwords, JWT secrets, raw tokens, or internal credentials in responses or logs.
Existing URL Contracts
Current URL prefixes are not fully uniform:

Authentication: /api/auth
User operations: /api/auth/* and /api/users/me
Recipient/family operations: /api/fm/family
Gift operations: /api/gm/gift
Simulation operations: /api/gs
FAQ and consultation operations: /api/ai
Do not rename or normalize these paths as incidental cleanup. URL changes are API contract changes and require explicit approval, updated tests, and coordination with API consumers.

HTTP Status and Body Status
ApiResponse.statusCode is an application response code.
It is not guaranteed to equal the actual HTTP response status.
Some existing controller methods return a body code such as CREATED(201) while the HTTP response remains 200.
Do not silently change this behavior while working on unrelated code.
When a task intentionally changes HTTP semantics, update controller annotations or ResponseEntity, response assertions, and API documentation together.
Authentication and Authorization
JWT authentication is stateless.
JwtAuthFilter reads the Authorization header and places the token subject in the Spring Security principal.
The principal is currently a string representation of userId.
Use the authenticated principal to determine the current user.
Use CurrentUser.id(principal) where the surrounding controller already follows that pattern.
Existing user controllers may resolve the ID from Authentication; preserve the local convention unless refactoring the complete flow.
Never trust a client-provided user ID for ownership or authorization decisions.
Always scope recipient, gift, simulation, and profile access to the authenticated user.
Verify ownership in the service or mapper layer before reading, changing, or deleting user-owned data.
The current SecurityConfig permits requests globally and relies on endpoint-level principal validation in several flows.
Do not assume that every route is blocked automatically by Spring Security.
Do not broaden or tighten global authorization rules as an incidental change; security-policy changes require endpoint review and corresponding tests.
Access and refresh token validation, token type, subject matching, expiration, and revocation behavior must remain distinct.
Do not log complete JWT values.
Service and Transaction Rules
Controllers should translate HTTP input into service calls and build API responses.
Services own business validation, normalization, authorization checks, and orchestration.
Mapper interfaces should contain database operations, not business decisions.
Add @Transactional to service methods that perform multiple dependent writes.
Use @Transactional(readOnly = true) for complex read flows when appropriate.
Do not add transactions to controllers.
Check affected-row counts where the existing domain treats zero or multiple rows as an error.
Translate expected database failures into the repository's exception and response-code conventions.
Preserve input-normalization behavior such as trimming and lowercasing email addresses.
MyBatis Rules
Every mapper interface and mapper XML namespace must match exactly.
Every mapper method name and XML statement id must match exactly.
When changing a mapper method:
update the Java mapper interface;
update the mapper XML;
update every service call;
update test fakes and stubs implementing that mapper;
update mapper XML parsing tests when relevant.
Use @Param when a mapper method has multiple parameters.
Keep @Param names synchronized with XML placeholders.
Avoid SELECT *; list columns explicitly.
Use resultMap for nontrivial mappings.
Database tables and columns use snake_case.
Java properties use camelCase.
MyBatis has mapUnderscoreToCamelCase enabled, but explicit result maps should still be used where mapping clarity matters.
Keep SQL in mapper XML unless a nearby mapper establishes another pattern.
Scope update and delete statements by both resource ID and owning user ID when ownership is required.
After modifying XML, run an XML parsing test or a relevant mapper/integration test.
Database Changes
There is currently no established Flyway or Liquibase migration workflow.
Do not add a migration framework or rewrite the database schema without explicit approval.
Coordinate schema changes with mapper XML, domain objects, DTOs, tests, and documentation.
Do not place production credentials or environment-specific connection details in SQL files.
Use transactions in database integration tests so test data is rolled back where possible.
Clearly state when verification requires a local MySQL database or schema data.
Simulation and Tax Calculation Rules
Treat tax, deduction, tranche, date-window, and monetary calculations as high-risk business logic.
Do not change formulas, rounding, date boundaries, deduction windows, scenario ordering, or status transitions without explicit requirements.
Use integer monetary types such as long or Long consistently with existing code; do not introduce floating-point arithmetic for currency.
Preserve deterministic calculation behavior.
Add focused boundary tests for calculation changes.
Relevant boundaries include:
zero and negative amounts;
exact tax-bracket boundaries;
dates exactly on a ten-year window boundary;
empty gift histories;
multiple recipients;
immediate versus installment scenarios;
save and idempotency behavior.
Simulation save flows must revalidate server-side calculations rather than trusting client-calculated values.
Preserve idempotency-key behavior when changing simulation execution or save flows.
Batch Module Rules
batch is a standalone Spring Batch application with BatchApplication as its main class.
batch depends on core; core must never depend on batch.
Do not introduce circular module dependencies.
Batch configuration uses batch.properties and its own database.properties.
Keep batch mapper interfaces and XML under the batch package and resource paths.
Configure jobs as explicit Job, Step, reader, processor, and writer components.
Use the configured chunk size instead of scattering unexplained chunk-size literals when extending the implementation.
Use unique job parameters when repeated executions are expected.
Do not replace a mock reader with an external API call without defining timeout, retry, failure, and test behavior.
Do not use System.out for new operational logging; use Log4j2.
Never log external API credentials or complete response bodies containing sensitive information.
Java Coding Conventions
Use Java 17 language features only.
Follow the style of nearby files instead of applying a repository-wide reformat.
Use four spaces for indentation.
Use descriptive class, method, and variable names.
Class names use PascalCase.
Methods and variables use camelCase.
Constants use UPPER_SNAKE_CASE.
Prefer constructor injection.
Use Lombok only where it is already supported and improves consistency.
Use records for immutable DTOs when consistent with the surrounding domain.
Do not convert existing DTOs or domain objects between records and Lombok classes as unrelated cleanup.
Keep controllers thin.
Avoid duplicated validation and calculation logic.
Use Log4j2 through @Log4j2 for application logging.
Use parameterized logging instead of string concatenation for new logs.
Do not log passwords, tokens, personal identifiers, or unnecessary personal data.
Remove debugging output before completing a change.
Add comments for non-obvious business rules, not for code that is already self-explanatory.
Testing Rules
Add or update tests for every behavior change and bug fix.
Prefer the narrowest useful test during development, then run the broader affected module tests.
Test names and @DisplayName values should describe observable behavior.
Controller tests should verify:
request validation;
authentication and ownership;
response body codes;
actual HTTP status where contractually relevant;
success and error payload shapes.
Service tests should verify business rules independently of HTTP.
Mapper changes should include XML parsing or database integration verification.
Security changes should include valid, expired, malformed, revoked, and wrong-token-type cases where relevant.
Tests requiring MySQL are integration tests and depend on local ignored property files and a compatible schema.
Do not claim full verification when database-dependent tests were skipped or could not connect.
If the existing test suite fails before a change, distinguish baseline failures from failures introduced by the change.
Never hide a failure by deleting assertions, disabling tests, or using -x test as final verification.
When a mapper interface changes, search for every fake implementation in tests and update it before reporting completion.
Current Verification Caveat
At the time this file was prepared, .\gradlew.bat test did not compile the complete test suite because fake GiftMapper implementations were not synchronized with the current mapper interface.

Affected test classes included:

GiftControllerTest
RecipientControllerTest
RecipientServiceTest
The missing current mapper method was selectTaxBracket(LocalDate, Long), while stale test implementations still contained an incompatible selectDeduction override.

Recheck this baseline before beginning a task. Remove this section once the test suite has been repaired and confirmed.

Dependency Policy
Check whether existing dependencies can solve the task before adding a new library.
Ask for confirmation before adding a new production dependency.
Keep shared dependency versions in the root build.gradle.
Add module-specific dependencies only to the module that uses them.
Avoid duplicate dependency declarations already inherited from another module.
If Node development dependencies change, update both package.json and package-lock.json.
Do not commit generated Gradle caches, build outputs, IDE files, or node_modules.
Verification Checklist
Before completing a code change:

Inspect all changed files and their callers.
Search for mapper XML, tests, DTOs, and consumers affected by signature changes.
Compile the affected production modules.
Run the narrowest relevant tests.
Run the affected module test task when possible.
Run the full test task for cross-module or shared changes when possible.
Confirm that no local property files or secrets were staged.
Confirm that no unrelated files were reformatted or modified.
Report exactly which commands ran and whether they passed.
Report skipped verification and baseline failures explicitly.
Recommended minimum verification:

Core-only change:
.\gradlew.bat :core:compileJava
relevant :core:test --tests ...
.\gradlew.bat :core:test when possible
Batch-only change:
.\gradlew.bat :batch:compileJava
relevant batch test
.\gradlew.bat :batch:test when possible
Shared mapper, domain, security, or Gradle change:
.\gradlew.bat test
.\gradlew.bat build when possible
Git and Review
Do not create branches, commits, pushes, issues, or pull requests unless the user requests them.
Branches created by the repository workflow follow:
feature/KAN-123_short-description
fix/KAN-123_short-description
refactor/KAN-123_short-description
Commit headers must follow the configured commitlint pattern:
KAN-{number} {type}: {message}
Allowed commit types:

feat
fix
docs
style
refactor
perf
test
chore
build
Example:

KAN-123 fix: synchronize gift mapper test fakes
Keep refactors separate from functional changes when practical.
PR descriptions should identify API changes, database changes, tests, and related Jira/GitHub issues.
Do not commit generated build/, .gradle/, .idea/, node_modules/, or local secret files.
Code Review Checklist
When reviewing changes, check for:

API URL or response-contract regressions
mismatch between HTTP status and intended body status
missing authentication or ownership checks
exposure of passwords, tokens, credentials, or personal data
mapper interface/XML/test-fake mismatches
update or delete queries missing user ownership conditions
missing validation activation through @Valid or @Validated
transaction boundaries around dependent writes
unsafe changes to tax calculations, rounding, or date windows
floating-point use for monetary values
module dependency direction violations
database-dependent tests reported as passing without a database
local configuration or generated files accidentally included

추가로, `Current Verification Caveat`처럼 일시적인 실패 내용을 `AGENTS.md`에 넣는 것은 선택 사항입니다. 현재 `.\gradlew.bat test`는 실제로 다음 이유로 실패했습니다.

- `GiftMapper`에 `selectTaxBracket(LocalDate, Long)`가 추가됨
- 세 테스트의 `FakeGiftMapper`가 새 메서드를 구현하지 않음
- 이전 `selectDeduction` 시그니처에는 잘못된 `@Override`가 남아 있음

팀이 `AGENTS.md`를 장기 규칙만 담는 문서로 관리하려면 해당 섹션은 빼고, GitHub 이슈나 README의 알려진 문제에 옮기는 편이 더 깔끔합니다. 나머지 내용은 현재 저장소 구조에 그대로 적용할 수 있습니다.
(일요일) 오후 3:49
[언어 및 표현 규칙]
AI 처리 과정 메시지와 응답 메시지는 모두 한국어로 작성한다.
처리 과정 메시지를 제외한 모든 응답에는 존댓말을 사용한다.
응답에 한글 인코딩 깨짐 또는 타 언어 혼입이 보이면 즉시 전체 문장을 한국어로 다시 작성한다.
UTF-8 인코딩이 깨지지 않았는지 확인한다.
�� 등의 깨진 문자열이 포함되면 전체 문장을 재작성한다.

[코드 변경 원칙]

사용자가 요청한 범위를 벗어난 파일은 수정하지 않는다.
이해하지 못한 요구 사항이 있으면 반드시 사용자에게 되묻는다.
디버그 로그는 문제가 해결되면 반드시 삭제한다.
추측성 수정은 금지한다. 원인을 모르면 수정하지 않는다.

[보안 및 운영 원칙]

보안에 취약한 코드는 특별한 지시가 없다면 사용하지 않는다.
간단하지 않은 문제는 웹 검색을 통해 최신 해결책을 확인한다.
이 프로젝트는 프로덕션 환경이다. 오류 수정을 위해 임시 우회 코드를 작성하지 않는다.
코드 작업 시 docs/ 폴더의 모든 Markdown 문서를 숙지하고 규칙을 준수한다. 해당 폴더가 없으면 이 지침은 적용하지 않는다.
이 내용도 추가해서 다시 알려줘

…

더 보기
오전 9:35
요청하신 내용을 반영하면 AGENTS.md 자체도 한국어로 작성하는 것이 일관적입니다. 파일은 직접 수정하지 않았으며, 아래 내용을 그대로 붙여 넣으시면 됩니다.

# AGENTS.md

## 적용 범위

- 이 지침은 백엔드 저장소 전체에 적용한다.
- 하위 디렉터리에 별도의 `AGENTS.md`가 있으면 해당 디렉터리에서는 더 구체적인 지침을 우선 적용한다.
- 작업 전 관련 구현 코드, 설정, MyBatis XML, 테스트를 먼저 확인한다.
- 현재 코드와 빌드 설정, 테스트를 README보다 우선적인 기준으로 사용한다.
- 사용자가 명시적으로 요청하지 않으면 이 파일을 수정하지 않는다.

## 언어 및 표현 규칙

- AI의 처리 과정 메시지와 최종 응답은 모두 한국어로 작성한다.
- 처리 과정 메시지를 제외한 사용자 응답에는 존댓말을 사용한다.
- 클래스명, 메서드명, 명령어, 경로 등 기술적으로 필요한 경우에만 영문을 사용한다.
- 한글 인코딩이 깨지거나 의도하지 않은 다른 언어가 섞이지 않았는지 응답 전에 확인한다.
- 깨진 문자열이나 대체 문자가 발견되면 해당 문장만 부분 수정하지 말고 전체 문장을 올바른 한국어로 다시 작성한다.
- 새로 작성하거나 수정하는 텍스트 파일은 UTF-8 인코딩을 유지한다.
- 기존 파일에서 인코딩이 깨진 부분을 발견하더라도 요청 범위 밖이라면 임의로 전체 파일을 변환하지 않는다.
- 로그, 예외 메시지, 테스트 이름, 주석을 추가할 때도 한글 인코딩이 정상인지 확인한다.

## 코드 변경 원칙

- 사용자가 요청한 범위를 벗어난 파일은 수정하지 않는다.
- 관련 없는 코드 정리, 이름 변경, 전체 포매팅을 함께 수행하지 않는다.
- 기존 사용자의 변경 사항을 보존하고 덮어쓰지 않는다.
- 요구 사항을 이해하지 못했거나 서로 다르게 해석될 가능성이 크면 반드시 사용자에게 확인한다.
- 사실을 확인할 수 있는 경우에는 파일, 테스트, 설정을 먼저 조사한 후 판단한다.
- 원인을 확인하지 못한 상태에서 추측으로 코드를 수정하지 않는다.
- 오류 수정은 재현, 원인 확인, 수정, 검증 순서로 진행한다.
- 프로덕션 코드에 임시 우회 로직, 하드코딩된 예외 처리, 검증 생략 코드를 추가하지 않는다.
- 디버깅을 위해 추가한 로그, 출력문, 임시 플래그는 문제가 해결되면 반드시 삭제한다.
- 테스트를 통과시키기 위해 검증 로직이나 기존 assertion을 약화하지 않는다.
- 사용자가 요청하지 않은 대규모 리팩터링이나 아키텍처 변경을 수행하지 않는다.

## 보안 및 운영 원칙

- 이 저장소는 프로덕션 환경에 사용되는 코드로 간주한다.
- 특별한 지시가 없다면 보안에 취약한 구현을 사용하지 않는다.
- 비밀번호, JWT, API 키, 데이터베이스 자격 증명, 개인정보를 코드나 로그에 노출하지 않는다.
- 클라이언트가 전달한 사용자 ID를 소유권 검증 없이 신뢰하지 않는다.
- 인증된 사용자 식별자를 기준으로 데이터 접근 권한을 확인한다.
- 보안 검증을 생략하거나 모든 요청을 허용하는 임시 우회 코드를 추가하지 않는다.
- 운영 장애를 숨기기 위해 예외를 무시하거나 성공 응답으로 바꾸지 않는다.
- 외부 API 호출을 추가할 때 시간 제한, 실패 처리, 재시도 여부, 민감정보 로깅 여부를 검토한다.
- 간단하지 않은 문제는 웹 검색을 통해 최신 해결 방법과 보안 권고를 확인한다.
- 기술 문제를 검색할 때는 공식 문서, 표준 문서, 원본 저장소와 같은 1차 자료를 우선한다.
- 웹 검색을 사용할 수 없으면 검색하지 못한 사실과 검증하지 못한 부분을 사용자에게 알린다.
- 검색 결과를 그대로 적용하지 말고 현재 프로젝트 버전과 구조에 맞는지 확인한다.

## 프로젝트 문서 확인

- 코드 작업을 시작하기 전에 `docs/` 디렉터리의 존재 여부를 확인한다.
- `docs/` 디렉터리가 있으면 그 안의 모든 Markdown 문서를 읽고 관련 규칙을 준수한다.
- 하위 디렉터리를 포함하여 `docs/**/*.md` 문서를 확인한다.
- `docs/` 디렉터리가 없으면 이 문서 확인 규칙은 적용하지 않는다.
- 문서와 실제 코드가 충돌하면 임의로 판단하지 말고 충돌 내용을 사용자에게 알린다.
- README에는 계획 중인 기능이나 현재 코드와 다른 예시가 포함될 수 있으므로 실제 구현과 교차 확인한다.

## 프로젝트 개요

- 이 저장소는 증여 계획 및 증여세 시뮬레이션 서비스 `MiriZoom`의 백엔드이다.
- Java 17과 Spring Framework 5.3을 사용한다.
- Spring Boot 프로젝트가 아닌 Spring Legacy 프로젝트이다.
- Gradle 멀티모듈 프로젝트로 구성되어 있다.
- `core` 모듈은 Spring MVC 기반 REST API이며 WAR 파일로 빌드된다.
- `batch` 모듈은 Spring Batch 애플리케이션이며 `core` 모듈에 의존한다.
- 데이터베이스 접근에는 MyBatis, MySQL, HikariCP를 사용한다.
- 인증에는 Spring Security와 JWT를 사용한다.
- 테스트에는 JUnit 5와 Spring Test를 사용한다.

## 개발 환경

- Java 17을 사용한다.
- 시스템에 설치된 Gradle 대신 저장소의 Gradle Wrapper를 사용한다.
- Windows에서는 `.\gradlew.bat`를 사용한다.
- macOS와 Linux에서는 `./gradlew`를 사용한다.
- 로컬 설정 파일은 다음 예시 파일을 복사하여 만든다.
  - `core/src/main/resources/application.properties.example`
  - `core/src/main/resources/database.properties.example`
  - `batch/src/main/resources/database.properties.example`
- 다음 로컬 설정 파일은 비밀정보가 포함될 수 있으므로 커밋하지 않는다.
  - `core/src/main/resources/application.properties`
  - `core/src/main/resources/database.properties`
  - `core/src/main/resources/security.properties`
  - `batch/src/main/resources/database.properties`
- Spring Boot 전환 작업이 아니라면 YAML 설정이나 Spring Boot 자동 설정을 도입하지 않는다.

## 주요 명령어

### Windows

```powershell
# 프로덕션 소스 컴파일
.\gradlew.bat :core:compileJava :batch:compileJava

# 전체 테스트
.\gradlew.bat test

# core 모듈 테스트
.\gradlew.bat :core:test

# 특정 테스트 클래스 실행
.\gradlew.bat :core:test --tests "com.example.project.package.ClassName"

# 전체 빌드
.\gradlew.bat build

# core WAR 빌드
.\gradlew.bat :core:war

# batch 실행
.\gradlew.bat :batch:run
테스트 제외 빌드는 사용자가 명시적으로 요청하거나 테스트와 무관한 빌드 확인이 필요한 경우에만 사용한다.
테스트 제외 빌드를 전체 검증 완료로 보고하지 않는다.
프로젝트 구조
build.gradle: 공통 버전 및 Java 설정
settings.gradle: core, batch 모듈 등록
core/build.gradle: Spring MVC WAR 및 core 테스트 설정
batch/build.gradle: Spring Batch와 core 모듈 의존성 설정
core/src/main/java/com/example/project/: 웹 애플리케이션 코드
core/src/main/resources/mapper/: core MyBatis XML
core/src/main/webapp/WEB-INF/views/: JSP 오류 및 기본 화면
core/src/test/java/: 단위, MVC, XML 파싱, DB 통합 테스트
batch/src/main/java/com/example/project/batch/: 배치 설정 및 작업
batch/src/main/resources/mapper/: batch MyBatis XML
.github/: 이슈, PR, Jira 연동 설정
.husky/: 테스트 및 커밋 메시지 검사 훅
core 모듈 구조
현재 주요 도메인은 다음과 같다.

auth: 로그인, 토큰 갱신, 로그아웃
user: 회원가입과 회원정보 관리
recipient: 수증자와 가족정보 관리
gift: 증여 내역, 공제, 신고정보, 상태 관리
simulation: 증여 및 세금 시뮬레이션
consultation: FAQ와 상담
product: 금융상품 관련 공통 영역
security: JWT와 Spring Security
common: 공통 응답, 예외, 웹 유틸리티
config: Spring MVC 및 애플리케이션 설정
도메인 내부에서는 기존 구조를 따른다.

controller: HTTP 요청 및 응답 처리
service: 비즈니스 규칙, 권한 확인, 트랜잭션
mapper: MyBatis 인터페이스
domain: 영속성 및 비즈니스 객체
dto/request: 요청 DTO
dto/response: 응답 DTO
exception: 도메인 예외
작업과 무관하게 Repository 패턴, 공통 부모 Controller, Service 인터페이스 등의 새로운 구조를 강제하지 않는다.

Spring Legacy 규칙
주요 설정 클래스는 RootConfig, ServletConfig, WebConfig, SecurityConfig이다.
WebConfig가 AbstractAnnotationConfigDispatcherServletInitializer를 통해 애플리케이션을 초기화한다.
core 모듈은 Servlet 컨테이너에 WAR로 배포한다.
명시적인 마이그레이션 요청이 없다면 Spring Boot 애플리케이션 클래스나 내장 서버를 추가하지 않는다.
루트 컨텍스트와 MVC 컨텍스트의 컴포넌트 스캔 범위를 유지한다.
의존성 주입에는 생성자 주입을 사용한다.
주변 코드와 일치하는 경우 Lombok의 @RequiredArgsConstructor를 사용할 수 있다.
Controller에는 복잡한 비즈니스 로직을 작성하지 않는다.
Service에는 HTTP 요청 및 응답 객체에 종속된 로직을 작성하지 않는다.
API 응답 규칙
기존 예외가 없다면 API 응답은 ApiResponse<T>를 사용한다.
응답의 path에는 HttpServletRequest#getRequestURI() 값을 사용한다.
적절한 ResponseCode가 이미 있으면 재사용한다.
같은 의미를 가진 응답 코드를 중복해서 추가하지 않는다.
공통 비즈니스 오류는 ServiceException으로 전달한다.
시뮬레이션 영역은 기존 SimulationException 규칙을 따른다.
요청 DTO에는 필요한 Bean Validation 규칙을 작성한다.
검증이 실제로 실행되도록 @Valid 또는 @Validated를 사용한다.
비밀번호, 토큰, 비밀키를 응답 DTO에 포함하지 않는다.
현재 API 경로
현재 API 경로는 도메인별로 완전히 통일되어 있지 않다.

인증: /api/auth
회원: /api/auth/*, /api/users/me
수증자 및 가족: /api/fm/family
증여: /api/gm/gift
시뮬레이션: /api/gs
FAQ 및 상담: /api/ai
관련 없는 작업에서 위 경로를 임의로 통일하거나 변경하지 않는다. 경로 변경은 프런트엔드 등 API 소비자와의 계약 변경으로 취급한다.

HTTP 상태와 본문 상태 코드
ApiResponse.statusCode는 애플리케이션 응답 코드이다.
실제 HTTP 상태와 항상 같다고 가정하지 않는다.
일부 기존 API는 본문에 CREATED(201)를 반환하면서 실제 HTTP 상태는 200을 반환한다.
관련 없는 작업에서 이 동작을 임의로 변경하지 않는다.
HTTP 상태를 의도적으로 변경하는 경우 Controller, 테스트, API 문서를 함께 수정한다.
인증 및 권한 규칙
JWT 인증은 세션을 사용하지 않는 방식이다.
JwtAuthFilter는 Authorization 헤더를 읽어 사용자 ID를 principal로 저장한다.
현재 principal은 문자열 형태의 사용자 ID이다.
주변 Controller가 사용하는 방식에 따라 CurrentUser.id(principal) 또는 Authentication을 사용한다.
클라이언트가 요청 본문이나 쿼리로 전달한 사용자 ID를 인증 정보 대신 사용하지 않는다.
회원, 수증자, 증여, 시뮬레이션 데이터 접근 시 소유권을 확인한다.
수정 및 삭제 쿼리는 필요한 경우 리소스 ID와 사용자 ID를 함께 조건으로 사용한다.
현재 SecurityConfig는 요청을 전역적으로 허용하고 일부 엔드포인트에서 principal을 직접 검증하는 과도기 구조이다.
모든 API가 Spring Security에 의해 자동 차단된다고 가정하지 않는다.
관련 API 전체를 검토하지 않고 전역 접근 정책을 변경하지 않는다.
Access Token과 Refresh Token의 용도, 만료, 토큰 유형, subject, 폐기 상태를 구분하여 검증한다.
JWT 전체 값을 로그로 출력하지 않는다.
Service 및 트랜잭션 규칙
Controller는 요청 변환, Service 호출, 응답 구성을 담당한다.
Service는 입력 정규화, 권한 확인, 비즈니스 규칙, 작업 조합을 담당한다.
Mapper에는 복잡한 비즈니스 판단을 넣지 않는다.
여러 쓰기 작업이 하나의 단위로 성공해야 하면 Service에 @Transactional을 적용한다.
복잡한 조회 작업은 필요한 경우 @Transactional(readOnly = true)를 사용한다.
Controller에는 트랜잭션을 선언하지 않는다.
데이터 변경 건수가 중요한 작업은 Mapper 반환 건수를 확인한다.
이메일 소문자 변환과 공백 제거 등 기존 입력 정규화 동작을 유지한다.
MyBatis 규칙
Mapper 인터페이스의 전체 이름과 XML의 namespace를 일치시킨다.
Mapper 메서드명과 XML 명령문의 id를 일치시킨다.
Mapper 메서드를 변경할 때 다음 항목을 모두 확인한다.
Java Mapper 인터페이스
Mapper XML
해당 Mapper를 사용하는 Service
테스트의 가짜 Mapper와 스텁
관련 XML 파싱 테스트
매개변수가 여러 개라면 @Param을 사용한다.
@Param 이름과 XML의 플레이스홀더 이름을 일치시킨다.
SELECT * 대신 컬럼을 명시한다.
복잡한 결과는 resultMap을 사용한다.
데이터베이스 테이블과 컬럼에는 snake_case를 사용한다.
Java 속성에는 camelCase를 사용한다.
동적 SQL은 필요한 경우에만 <if>, <choose>, <where> 등을 사용한다.
사용자 소유 데이터의 수정 및 삭제 SQL에는 소유권 조건이 누락되지 않았는지 확인한다.
데이터베이스 변경
현재 Flyway 또는 Liquibase 기반 마이그레이션 절차가 확립되어 있지 않다.
명시적인 요청 없이 마이그레이션 프레임워크를 도입하지 않는다.
스키마를 변경하면 Mapper XML, 도메인 객체, DTO, 테스트, 문서를 함께 검토한다.
SQL 파일에 운영 데이터베이스 자격 증명을 넣지 않는다.
데이터베이스 통합 테스트에는 가능한 경우 트랜잭션 롤백을 사용한다.
테스트에 로컬 MySQL과 특정 스키마가 필요하면 이를 검증 결과에 명확히 적는다.
시뮬레이션 및 세금 계산 규칙
세율, 공제, 과세표준, 기간, 분할 증여 계산은 고위험 비즈니스 로직으로 취급한다.
명확한 요구 사항 없이 계산식, 반올림, 날짜 경계, 공제 기간을 변경하지 않는다.
통화 계산에는 기존 코드와 같이 long 또는 Long을 사용한다.
통화 계산에 float 또는 double을 도입하지 않는다.
계산 변경 시 경계값 테스트를 추가한다.
다음 조건을 우선적으로 확인한다.
0원 또는 음수 금액
세율 구간의 정확한 경계값
10년 기간 경계일
증여 이력이 없는 경우
수증자가 여러 명인 경우
즉시 증여와 분할 증여
저장 및 멱등성 처리
클라이언트가 전달한 계산 결과를 그대로 신뢰하지 않는다.
저장 시 서버 계산과 검증하는 기존 동작을 유지한다.
시뮬레이션 실행 및 저장 로직을 변경할 때 멱등성 키 동작을 유지한다.
batch 모듈 규칙
batch는 BatchApplication을 진입점으로 사용하는 독립 실행 애플리케이션이다.
batch는 core에 의존할 수 있지만 core는 batch에 의존하면 안 된다.
순환 모듈 의존성을 만들지 않는다.
batch는 별도의 database.properties와 batch.properties를 사용한다.
Job, Step, Reader, Processor, Writer 역할을 분리한다.
반복 실행이 필요한 작업에는 고유한 Job Parameter를 사용한다.
모의 Reader를 외부 API 호출로 변경할 때는 시간 제한, 실패 처리, 재시도, 테스트 전략을 함께 정의한다.
새 운영 로그에는 System.out 대신 Log4j2를 사용한다.
외부 API 자격 증명이나 민감한 전체 응답을 로그로 출력하지 않는다.
Java 작성 규칙
Java 17에서 지원하는 문법만 사용한다.
주변 코드 스타일을 따른다.
요청과 무관한 전체 파일 포매팅을 수행하지 않는다.
들여쓰기는 공백 4칸을 사용한다.
클래스명은 PascalCase를 사용한다.
메서드와 변수명은 camelCase를 사용한다.
상수는 UPPER_SNAKE_CASE를 사용한다.
생성자 주입을 우선한다.
주변 코드와 일치하는 경우에만 Lombok을 사용한다.
기존 DTO를 관련 없는 작업에서 record나 Lombok 클래스로 일괄 변환하지 않는다.
중복된 검증 및 계산 로직을 만들지 않는다.
애플리케이션 로그는 Log4j2를 사용한다.
새 로그는 문자열 연결 대신 매개변수 방식을 사용한다.
개인정보, 비밀번호, 토큰을 로그로 출력하지 않는다.
복잡한 비즈니스 규칙에는 이유를 설명하는 주석을 작성한다.
코드 자체로 명확한 내용을 반복 설명하는 주석은 작성하지 않는다.
테스트 규칙
동작 변경과 오류 수정에는 관련 테스트를 추가하거나 수정한다.
개발 중에는 가장 좁은 범위의 테스트부터 실행한다.
완료 전에는 가능한 범위에서 해당 모듈 전체 테스트를 실행한다.
Controller 테스트에서는 다음을 확인한다.
요청값 검증
인증과 소유권
본문 응답 코드
실제 HTTP 상태
성공 및 실패 응답 구조
Service 테스트에서는 HTTP와 분리된 비즈니스 규칙을 확인한다.
Mapper 변경에는 XML 파싱 테스트 또는 DB 통합 테스트를 수행한다.
보안 변경에는 정상, 만료, 변조, 폐기, 잘못된 토큰 유형을 검토한다.
MySQL이 필요한 통합 테스트를 실행하지 못했으면 전체 테스트가 통과했다고 보고하지 않는다.
기존 테스트가 작업 전부터 실패했다면 기준 실패와 새 변경으로 발생한 실패를 구분한다.
실패를 숨기기 위해 테스트를 비활성화하거나 assertion을 삭제하지 않는다.
Mapper 인터페이스를 변경하면 테스트의 모든 가짜 구현체를 검색하여 동기화한다.
현재 테스트 기준 상태
이 문서를 작성하기 위해 확인했을 때 .\gradlew.bat test는 테스트 컴파일 단계에서 실패했다.

확인된 원인은 다음과 같다.

현재 GiftMapper에는 selectTaxBracket(LocalDate, Long) 메서드가 존재한다.
GiftControllerTest의 FakeGiftMapper가 해당 메서드를 구현하지 않는다.
RecipientControllerTest의 FakeGiftMapper가 해당 메서드를 구현하지 않는다.
RecipientServiceTest의 FakeGiftMapper가 해당 메서드를 구현하지 않는다.
일부 가짜 Mapper에는 현재 인터페이스와 맞지 않는 selectDeduction 오버라이드가 남아 있다.
작업을 시작할 때 이 실패가 여전히 존재하는지 다시 확인한다. 문제가 수정되어 전체 테스트 통과가 확인되면 이 절을 삭제하거나 최신 상태로 변경한다.

의존성 정책
새 라이브러리를 추가하기 전에 기존 의존성으로 해결할 수 있는지 확인한다.
프로덕션 의존성을 추가하기 전에는 사용자에게 확인한다.
공통 버전은 루트 build.gradle에서 관리한다.
모듈 전용 의존성은 사용하는 모듈에만 추가한다.
Node 개발 의존성을 변경하면 package.json과 package-lock.json을 함께 갱신한다.
빌드 결과물, Gradle 캐시, IDE 설정, node_modules를 커밋하지 않는다.
검증 절차
코드 변경을 완료하기 전에 다음 사항을 확인한다.

변경 파일과 호출 위치를 검토한다.
관련 DTO, Mapper XML, 테스트, API 소비자를 검색한다.
변경된 모듈의 프로덕션 소스를 컴파일한다.
가장 관련성이 높은 테스트를 실행한다.
가능한 경우 해당 모듈 전체 테스트를 실행한다.
공통 코드 변경이면 가능한 경우 전체 테스트와 빌드를 실행한다.
로컬 설정 파일과 비밀정보가 변경 또는 스테이징되지 않았는지 확인한다.
디버그 로그와 임시 코드가 남아 있지 않은지 확인한다.
요청 범위 밖의 파일이 수정되지 않았는지 확인한다.
실행한 검증 명령과 결과를 정확히 사용자에게 보고한다.
실행하지 못한 검증과 그 이유를 사용자에게 보고한다.
Git 및 리뷰 규칙
사용자가 요청하지 않으면 브랜치, 커밋, 푸시, 이슈, PR을 생성하지 않는다.
저장소 자동화에서 생성하는 브랜치는 다음 형식을 따른다.
feature/KAN-123_short-description
fix/KAN-123_short-description
refactor/KAN-123_short-description
커밋 메시지는 다음 형식을 따른다.
KAN-{번호} {유형}: {메시지}
허용되는 유형은 다음과 같다.

feat
fix
docs
style
refactor
perf
test
chore
build
예시는 다음과 같다.

KAN-123 fix: 증여 Mapper 테스트 구현 동기화
기능 변경과 대규모 리팩터링은 가능한 경우 분리한다.
PR에는 API 변경, 데이터베이스 변경, 테스트, 관련 Jira 및 GitHub 이슈를 작성한다.
build/, .gradle/, .idea/, node_modules/, 로컬 설정 파일을 커밋하지 않는다.
코드 리뷰 점검 사항
리뷰 시 다음 사항을 확인한다.

기존 API 경로 또는 응답 계약이 깨지지 않았는가
HTTP 상태와 본문 상태 코드 변경이 의도적인가
인증 또는 데이터 소유권 검증이 누락되지 않았는가
비밀번호, 토큰, 자격 증명, 개인정보가 노출되지 않았는가
Mapper 인터페이스, XML, 테스트 가짜 구현체가 동기화되었는가
수정 및 삭제 SQL에 사용자 소유권 조건이 필요한가
@Valid 또는 @Validated가 누락되지 않았는가
여러 쓰기 작업에 적절한 트랜잭션이 적용되었는가
세금 계산식, 반올림, 날짜 경계가 의도치 않게 변경되지 않았는가
통화 계산에 부동소수점이 사용되지 않았는가
core에서 batch를 의존하거나 순환 의존성이 생기지 않았는가
데이터베이스 테스트를 실행하지 않고 전체 통과로 보고하지 않았는가
임시 코드, 디버그 로그, 로컬 설정, 생성 파일이 포함되지 않았는가
docs/ 문서의 규칙과 변경 내용이 충돌하지 않는가

현재 저장소에는 `docs/` 디렉터리가 확인되지 않았으므로 지금은 문서 숙지 규칙이 적용되지 않습니다. 나중에 `docs/`가 생기면 코드 작업 전에 모든 Markdown 문서를 확인하도록 위 지침이 자동으로 적용됩니다.
오전 9:35




환경

변경 사항
+0
-0

로컬

feature/KAN-127_agents

커밋 또는 푸시
GitHub CLI를 사용할 수 없음

브랜치 비교
