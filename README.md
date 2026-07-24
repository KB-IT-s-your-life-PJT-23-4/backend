## 📌 목차

| 🚀 1. 프로젝트 개요 | 🛠️ 2. 아키텍처 & 설정 | 📜 3. 개발 규칙 & 컨벤션 | 🤝 4. 워크플로우 & 기타 |
| :--- | :--- | :--- | :--- |
| [`1. 프로젝트 소개`](#1-프로젝트-소개)<br>[`2. 개발 진행 상태`](#2-개발-진행-상태)<br>[`3. 기술 스택`](#3-기술-스택)<br>[`5. 주요 기능`](#5-주요-기능) | [`4. 시스템 구성`](#4-시스템-구성)<br>[`11. 프로젝트 디렉터리 구조`](#11-프로젝트-디렉터리-구조)<br>[`15. 개발 환경 설정`](#15-개발-환경-설정)<br>[`16. 프로젝트 설치 및 실행`](#16-프로젝트-설치-및-실행)<br>[`17. 환경 변수 설정`](#17-환경-변수-설정)<br>[`18. 실행 프로파일`](#18-실행-프로파일) | [`12. 계층별 역할`](#12-계층별-역할)<br>[`13. MyBatis 작성 규칙`](#13-mybatis-작성-규칙)<br>[`14. 데이터베이스 규칙`](#14-데이터베이스-규칙)<br>[`21. 예외 처리`](#21-예외-처리)<br>[`22. 로깅 규칙`](#22-로깅-규칙)<br>[`26. 코드 작성 규칙`](#26-코드-작성-규칙) | [`27. Git 브랜치 전략`](#27-git-브랜치-전략)<br>[`28. 커밋 메시지 규칙`](#28-커밋-메시지-규칙)<br>[`29. Pull Request 규칙`](#29-pull-request-규칙)<br>[`30. 협업 절차`](#30-협업-절차)<br>[`31. 개발 시 주의사항`](#31-개발-시-주의사항) |
| **🔗 5. API 명세** | **🛢️ 6. 데이터베이스 설정** | **⚙️ 7. 컴포넌트 구성** | **📝 8. 관리 및 지원** |
| [`6. API 구성`](#6-api-구성)<br>[`7. API 명세 예시`](#7-api-명세-예시)<br>[`8. 공통 응답 구조`](#8-공통-응답-구조)<br>[`9. HTTP 상태 코드 기준`](#9-http-상태-코드-기준)<br>[`10. 공통 상태 코드`](#10-공통-상태-코드) | [`19. 데이터베이스 설정`](#19-데이터베이스-설정) | [`20. 인증 및 인가`](#20-인증-및-인가)<br>[`23. 외부 API 연동`](#23-외부-api-연동)<br>[`24. Spring Batch 구성`](#24-spring-batch-구성)<br>[`25. 테스트`](#25-테스트) | [`32. TODO`](#32-todo)<br>[`33. 알려진 문제`](#33-알려진-문제)<br>[`34. 팀원 및 담당 기능`](#34-팀원-및-담당-기능)<br>[`35. 관련 문서`](#35-관련-문서)<br>[`36. 문의`](#36-문의) |

# MiriZoom(미리줌) Backend

프로젝트의 백엔드 애플리케이션 저장소입니다.

> 현재 개발 진행 중인 프로젝트입니다.
> API 명세, 데이터베이스 구조, 실행 환경은 개발 상황에 따라 변경될 수 있습니다.

---

## 1. 프로젝트 소개

### 프로젝트명

`MiriZoom`

### 프로젝트 개요

본 프로젝트는 증여 계획 수립부터 증여 내역 관리, 금융상품 비교, AI 세법 상담까지 제공하는 디지털 증여 관리 서비스의 백엔드 서버입니다.

회원 및 수증자 정보 관리, 증여세 시뮬레이션, 증여 내역 관리, AI 상담 서버 연동, 금융상품 및 세법 데이터 수집 기능을 제공합니다.

### 주요 기능

* 회원가입 및 로그인
* 사용자 및 수증자 정보 관리
* 증여 내역 관리
* 증여세 시뮬레이션
* 금융상품 비교
* AI 세법 상담 서버 연동
* 금융상품 및 세법 데이터 배치 처리

### 개발 기간

* 시작일: `2026-07-20`
* 종료 예정일: `2026-08-26`

---

## 2. 개발 진행 상태

| 구분    | 기능            | 상태   |
| ----- | ------------- | ---- |
| 공통    | 프로젝트 초기 설정    | 완료   |
| 공통    | 데이터베이스 연결     | 완료   |
| 공통    | 공통 응답 구조      | 진행 중 |
| 공통    | 예외 처리         | 진행 중 |
| 회원    | 회원가입 및 로그인    | 예정   |
| 회원    | 사용자 정보 관리     | 예정   |
| 증여    | 수증자 정보 관리     | 예정   |
| 증여    | 증여 내역 관리      | 예정   |
| 증여    | 증여세 시뮬레이션     | 예정   |
| 상품    | 금융상품 조회       | 예정   |
| AI    | FastAPI 서버 연동 | 예정   |
| Batch | 금융상품 데이터 수집   | 예정   |
| Batch | 세법 데이터 업데이트   | 예정   |

상태는 다음 기준으로 작성합니다.

* 예정
* 진행 중
* 코드 리뷰
* 테스트 중
* 완료
* 보류

---

## 3. 기술 스택

### Backend

| 구분                | 기술                              |
| ----------------- | ------------------------------- |
| Language          | ![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)                            |
| Framework         | ![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white) |
| Web               | ![Spring MVC](https://img.shields.io/badge/Spring_MVC-6DB33F?style=flat-square&logo=spring&logoColor=white)   |
| Security          | ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)                |
| ORM / SQL Mapper  | ![Hibernate](https://img.shields.io/badge/Hibernate-59666C?style=for-the-badge&logo=Hibernate&logoColor=white)  ![MyBatis](https://img.shields.io/badge/MyBatis-000000?style=flat-square&logoColor=white)                       |
| Database          | ![MySQL](https://img.shields.io/badge/mysql-4479A1.svg?style=for-the-badge&logo=mysql&logoColor=white)                           |
| Batch             | ![Spring Batch](https://img.shields.io/badge/Spring_Batch-6DB33F?style=flat-square&logo=spring&logoColor=white)                    |
| Authentication    | ![JWT](https://img.shields.io/badge/JWT-black?style=for-the-badge&logo=JSON%20web%20tokens)                          |
| Build Tool        | ![Gradle](https://img.shields.io/badge/Gradle-02303A.svg?style=for-the-badge&logo=Gradle&logoColor=white)                 |
| API Documentation | ![Swagger](https://img.shields.io/badge/-Swagger-%23Clojure?style=for-the-badge&logo=swagger&logoColor=white)      |
| Validation        | Bean Validation                 |
| Logging           | ![Log4j2](https://img.shields.io/badge/Log4j2-D22128?style=flat-square&logo=apache&logoColor=white)                |
| Test              | ![JUnit5](https://img.shields.io/badge/JUnit5-f5f5f5?style=for-the-badge&logo=junit5&logoColor=dc524a)                           |

### 외부 연동

| 구분              | 기술 또는 서버                       |
| --------------- | ------------------------------ |
| AI Server       | ![FastAPI](https://img.shields.io/badge/FastAPI-005571.svg?style=for-the-badge&logo=fastapi)                        |
| LLM             | ![ChatGPT](https://img.shields.io/badge/chatGPT-74aa9c?style=for-the-badge&logo=openai&logoColor=white)                     |
| 금융상품 데이터        | 금융상품 관련 외부 API                 |
| 세법 데이터          | 국세청 또는 공공데이터 API               |
| Vector Database | ChromaDB / Pinecone / Milvus 등 |
| Cache           | ![Redis](https://img.shields.io/badge/redis-%23DD0031.svg?style=for-the-badge&logo=redis&logoColor=white)                          |
| Web Server      | ![Nginx](https://img.shields.io/badge/nginx-%23009639.svg?style=for-the-badge&logo=nginx&logoColor=white)                          |

### 인프라 및 협업 도구

| 구분               | 도구                        |
| ---------------- | ------------------------- |
| IDE              | ![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?style=for-the-badge&logo=intellij-idea&logoColor=white)             |
| Version Control  | ![Git](https://img.shields.io/badge/git-%23F05033.svg?style=for-the-badge&logo=git&logoColor=white) ![GitHub](https://img.shields.io/badge/github-%23121011.svg?style=for-the-badge&logo=github&logoColor=white)               |
| Issue Management | ![Jira](https://img.shields.io/badge/jira-%230A0FFF.svg?style=for-the-badge&logo=jira&logoColor=white) ![GitHub Issues](https://img.shields.io/badge/GitHub_Issues-181717?style=flat-square&logo=github&logoColor=white)       |
| API Test         | ![Postman](https://img.shields.io/badge/Postman-FF6C37?style=for-the-badge&logo=postman&logoColor=white)                   |
| Database Tool    | ![MySQL Workbench](https://img.shields.io/badge/MySQL_Workbench-4479A1?style=flat-square&logo=mysql&logoColor=white) |
| Container        | ![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)                    |
| Cloud            | ![AWS](https://img.shields.io/badge/AWS-%23FF9900.svg?style=for-the-badge&logo=amazon-aws&logoColor=white)                  |

---

## 4. 시스템 구성

```text
Client
  │
  ▼
Vue.js Frontend
  │
  ▼
Nginx
  │
  ▼
Spring Backend
  ├─ 회원 관리
  ├─ 수증자 관리
  ├─ 증여 내역 관리
  ├─ 증여 시뮬레이션
  ├─ 금융상품 관리
  └─ AI 상담 연동
       │
       ▼
    FastAPI
       │
       ├─ OpenAI API
       └─ Vector Database

Spring Backend
  ├─ MyBatis ── MySQL
  ├─ Redis
  └─ Spring Batch
       ├─ 금융상품 API
       └─ 세법 및 공공데이터 API
```

### 서버별 역할

| 서버           | 역할                          |
| ------------ | --------------------------- |
| Vue.js       | 사용자 화면 및 입력 처리              |
| Nginx        | 정적 파일 제공, 리버스 프록시, HTTPS 처리 |
| Spring       | 핵심 비즈니스 로직 및 데이터 관리         |
| FastAPI      | AI 상담 요청 및 LLM 연동           |
| MySQL        | 회원, 증여, 상품, 상담 데이터 저장       |
| Redis        | 캐시, 토큰 또는 임시 데이터 관리         |
| Spring Batch | 외부 데이터 수집 및 정기 업데이트         |

---

## 5. 주요 기능

### 공통 기능

* 공통 API 응답 형식
* 요청 데이터 유효성 검사
* 전역 예외 처리
* API 요청 및 오류 로그 기록
* 인증 및 인가
* 페이징 처리
* 공통 상태 코드 관리

### 회원 관리

* 회원가입
* 로그인
* 로그아웃
* 사용자 정보 조회
* 사용자 정보 수정
* 회원 탈퇴
* 비밀번호 변경
* JWT Access Token 발급
* Refresh Token 관리

### 수증자 관리

* 수증자 등록
* 수증자 정보 조회
* 수증자 정보 수정
* 수증자 삭제
* 사용자와 수증자 관계 관리
* 수증자별 누적 증여액 조회

### 증여 내역 관리

* 증여 내역 등록
* 증여 내역 조회
* 증여 내역 수정
* 증여 내역 삭제
* 기간별 증여 내역 조회
* 수증자별 누적 증여 금액 계산
* 증여 공제 기준일 계산

### 증여 시뮬레이션

* 수증자 연령 및 관계에 따른 공제 한도 계산
* 과거 10년 증여 내역 조회
* 과세표준 계산
* 예상 증여세 계산
* 즉시 증여 시나리오 계산
* 분할 증여 시나리오 계산
* 금융상품 투자 결과 비교
* 시뮬레이션 결과 저장 및 조회

### 금융상품 관리

* 금융상품 목록 조회
* 금융상품 상세 조회
* 상품 유형별 조회
* 예상 수익률 계산
* 상품별 안정성 정보 조회
* 금융상품 데이터 등록 및 갱신

### AI 상담

* 사용자 질문 전달
* FastAPI 서버 호출
* AI 상담 답변 수신
* 관련 법령 및 출처 저장
* 상담 내역 저장
* 상담 내역 조회
* 은행원 또는 세무사 연결 요청

### Spring Batch

* 금융상품 데이터 정기 수집
* 상품 수익률 데이터 업데이트
* 세법 및 공제 기준 데이터 업데이트
* 오래된 데이터 정리
* 배치 실행 결과 기록
* 배치 실패 시 재시도 및 로그 기록

---

## 6. API 구성

### API 기본 경로

```text
/api
```

### 주요 API 경로

| 도메인   | 기본 경로                | 설명                |
| ----- | -------------------- | ----------------- |
| 인증    | `/api/auth`          | 회원가입, 로그인, 토큰 재발급 |
| 회원    | `/api/users`         | 사용자 정보 관리         |
| 수증자   | `/api/recipients`    | 수증자 정보 관리         |
| 증여 내역 | `/api/gifts`         | 증여 내역 관리          |
| 시뮬레이션 | `/api/simulations`   | 증여 시뮬레이션          |
| 금융상품  | `/api/products`      | 금융상품 조회           |
| AI 상담 | `/api/consultations` | AI 상담 및 상담 내역     |
| 관리자   | `/api/admin`         | 관리자 기능            |
| 배치    | `/api/admin/batches` | 배치 실행 및 결과 조회     |

---

## 7. API 명세 예시

### 회원가입

```http
POST /api/auth/signup
Content-Type: application/json
```

요청 예시:

```json
{
  "email": "user@example.com",
  "password": "password123!",
  "name": "홍길동"
}
```

응답 예시:

```json
{
  "success": true,
  "code": "201",
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동"
  }
}
```

### 로그인

```http
POST /api/auth/login
Content-Type: application/json
```

요청 예시:

```json
{
  "email": "user@example.com",
  "password": "password123!"
}
```

응답 예시:

```json
{
  "success": true,
  "code": "200",
  "message": "로그인에 성공했습니다.",
  "data": {
    "accessToken": "Access Token",
    "refreshToken": "Refresh Token",
    "user": {
      "userId": 1,
      "name": "홍길동"
    }
  }
}
```

### 수증자 등록

```http
POST /api/recipients
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청 예시:

```json
{
  "name": "김자녀",
  "birthDate": "2010-01-01",
  "relationship": "CHILD"
}
```

### 증여 시뮬레이션 실행

```http
POST /api/simulations
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청 예시:

```json
{
  "recipientId": 1,
  "giftAmount": 50000000,
  "investmentPeriod": 10,
  "productType": "DEPOSIT"
}
```

---

## 8. 공통 응답 구조

모든 API는 가능한 한 동일한 응답 구조를 사용합니다.

```json
{
  "success": true,
  "code": "200",
  "message": "요청이 정상적으로 처리되었습니다.",
  "data": {}
}
```

### 성공 응답 예시

```json
{
  "success": true,
  "code": "200",
  "message": "수증자 목록을 조회했습니다.",
  "data": [
    {
      "recipientId": 1,
      "name": "김자녀",
      "relationship": "CHILD"
    }
  ]
}
```

### 실패 응답 예시

```json
{
  "success": false,
  "code": "404",
  "message": "수증자 정보를 찾을 수 없습니다.",
  "data": null
}
```

### 유효성 검사 실패 예시

```json
{
  "success": false,
  "code": "400",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "email": "올바른 이메일 형식이 아닙니다.",
    "password": "비밀번호는 8자 이상이어야 합니다."
  }
}
```

---

## 9. HTTP 상태 코드 기준

| 상태 코드                       | 의미          | 사용 예시          |
| --------------------------- | ----------- | -------------- |
| `200 OK`                    | 요청 성공       | 조회, 수정, 삭제 성공  |
| `201 Created`               | 리소스 생성 성공   | 회원가입, 증여 내역 등록 |
| `204 No Content`            | 응답 본문 없는 성공 | 삭제 성공          |
| `400 Bad Request`           | 잘못된 요청      | 유효성 검사 실패      |
| `401 Unauthorized`          | 인증 실패       | 토큰 없음 또는 만료    |
| `403 Forbidden`             | 권한 부족       | 관리자 API 접근     |
| `404 Not Found`             | 리소스 없음      | 회원 또는 수증자 없음   |
| `409 Conflict`              | 데이터 충돌      | 이메일 중복         |
| `500 Internal Server Error` | 서버 내부 오류    | 처리되지 않은 예외     |

---

## 10. 공통 상태 코드

HTTP 상태 코드와 별도로 프로젝트 내부 상태 코드를 정의할 수 있습니다.

| 코드 범위    | 분류    | 설명                 |
| -------- | ----- | ------------------ |
| `1000번대` | 공통    | 공통 성공 및 오류         |
| `2000번대` | 회원    | 회원가입, 로그인, 인증      |
| `3000번대` | 증여    | 증여 내역 및 수증자        |
| `4000번대` | 시뮬레이션 | 세금 및 투자 계산         |
| `5000번대` | AI    | AI 상담 및 FastAPI 연동 |
| `6000번대` | Batch | 배치 처리              |
| `7000번대` | 금융상품  | 금융상품 조회 및 갱신       |

예시:

| 코드     | 메시지                     |
| ------ | ----------------------- |
| `1000` | 요청이 성공했습니다.             |
| `1001` | 입력값이 올바르지 않습니다.         |
| `2001` | 회원가입이 완료되었습니다.          |
| `2002` | 이메일 또는 비밀번호가 올바르지 않습니다. |
| `3001` | 수증자가 등록되었습니다.           |
| `3002` | 수증자를 찾을 수 없습니다.         |
| `4001` | 증여 시뮬레이션이 완료되었습니다.      |
| `5001` | AI 상담 답변 생성이 완료되었습니다.   |
| `5002` | AI 서버 연결에 실패했습니다.       |

---

## 11. 프로젝트 디렉터리 구조

### Spring Legacy 멀티 모듈 구조

```text
project-backend/
├─ build.gradle
├─ settings.gradle
├─ gradlew
├─ gradlew.bat
├─ gradle/
│  └─ wrapper/
├─ .gitignore
├─ README.md
│
├─ core/
│  ├─ build.gradle
│  │
│  └─ src/
│     ├─ main/
│     │  ├─ java/
│     │  │  └─ com/example/project/
│     │  │     ├─ config/
│     │  │     │  ├─ RootConfig.java
│     │  │     │  ├─ ServletConfig.java
│     │  │     │  ├─ SecurityConfig.java
│     │  │     │  ├─ WebConfig.java
│     │  │     │  └─ WebApplicationInitializer.java
│     │  │     │
│     │  │     ├─ common/
│     │  │     │  ├─ exception/
│     │  │     │  ├─ response/
│     │  │     │  ├─ security/
│     │  │     │  └─ util/
│     │  │     │
│     │  │     ├─ auth/
│     │  │     │  ├─ controller/
│     │  │     │  ├─ service/
│     │  │     │  ├─ dto/
│     │  │     │  └─ mapper/
│     │  │     │
│     │  │     ├─ user/
│     │  │     │  ├─ controller/
│     │  │     │  ├─ service/
│     │  │     │  ├─ mapper/
│     │  │     │  ├─ domain/
│     │  │     │  └─ dto/
│     │  │     │
│     │  │     ├─ recipient/
│     │  │     │  ├─ controller/
│     │  │     │  ├─ service/
│     │  │     │  ├─ mapper/
│     │  │     │  ├─ domain/
│     │  │     │  └─ dto/
│     │  │     │
│     │  │     ├─ gift/
│     │  │     │  ├─ controller/
│     │  │     │  ├─ service/
│     │  │     │  ├─ mapper/
│     │  │     │  ├─ domain/
│     │  │     │  └─ dto/
│     │  │     │
│     │  │     ├─ simulation/
│     │  │     │  ├─ controller/
│     │  │     │  ├─ service/
│     │  │     │  ├─ calculator/
│     │  │     │  ├─ domain/
│     │  │     │  └─ dto/
│     │  │     │
│     │  │     ├─ product/
│     │  │     │  ├─ controller/
│     │  │     │  ├─ service/
│     │  │     │  ├─ mapper/
│     │  │     │  ├─ domain/
│     │  │     │  └─ dto/
│     │  │     │
│     │  │     └─ consultation/
│     │  │        ├─ controller/
│     │  │        ├─ service/
│     │  │        ├─ client/
│     │  │        ├─ mapper/
│     │  │        ├─ domain/
│     │  │        └─ dto/
│     │  │
│     │  ├─ resources/
│     │  │  ├─ mapper/
│     │  │  │  ├─ auth/
│     │  │  │  ├─ user/
│     │  │  │  ├─ recipient/
│     │  │  │  ├─ gift/
│     │  │  │  ├─ product/
│     │  │  │  └─ consultation/
│     │  │  │
│     │  │  ├─ application.properties
│     │  │  ├─ database.properties
│     │  │  ├─ security.properties
│     │  │  ├─ mybatis-config.xml
│     │  │  └─ log4j2.xml
│     │  │
│     │  └─ webapp/
│     │     ├─ WEB-INF/
│     │     │  ├─ views/
│     │     │  └─ web.xml
│     │     └─ resources/
│     │        ├─ css/
│     │        ├─ js/
│     │        └─ images/
│     │
│     └─ test/
│        ├─ java/
│        │  └─ com/example/project/
│        │     ├─ user/
│        │     ├─ recipient/
│        │     ├─ gift/
│        │     ├─ simulation/
│        │     └─ consultation/
│        └─ resources/
│           └─ log4j2-test.xml
│
└─ batch/
   ├─ build.gradle
   │
   └─ src/
      ├─ main/
      │  ├─ java/
      │  │  └─ com/example/project/batch/
      │  │     ├─ config/
      │  │     │  ├─ BatchRootConfig.java
      │  │     │  ├─ BatchDataSourceConfig.java
      │  │     │  ├─ BatchMyBatisConfig.java
      │  │     │  └─ BatchSchedulerConfig.java
      │  │     │
      │  │     ├─ common/
      │  │     │  ├─ exception/
│     │  │     │  ├─ listener/
│     │  │     │  └─ util/
      │  │     │
      │  │     ├─ product/
      │  │     │  ├─ job/
      │  │     │  ├─ step/
      │  │     │  ├─ reader/
      │  │     │  ├─ processor/
      │  │     │  ├─ writer/
      │  │     │  ├─ client/
      │  │     │  ├─ mapper/
      │  │     │  └─ dto/
      │  │     │
      │  │     ├─ tax/
      │  │     │  ├─ job/
      │  │     │  ├─ step/
      │  │     │  ├─ reader/
      │  │     │  ├─ processor/
      │  │     │  ├─ writer/
      │  │     │  ├─ client/
      │  │     │  ├─ mapper/
      │  │     │  └─ dto/
      │  │     │
      │  │     └─ cleanup/
      │  │        ├─ job/
      │  │        ├─ step/
      │  │        └─ tasklet/
      │  │
      │  └─ resources/
      │     ├─ mapper/
      │     │  ├─ product/
      │     │  ├─ tax/
      │     │  └─ batch/
      │     │
      │     ├─ batch.properties
      │     ├─ database.properties
      │     ├─ mybatis-config.xml
      │     ├─ spring/
      │     │  ├─ batch-context.xml
      │     │  ├─ batch-job-context.xml
      │     │  └─ batch-scheduler-context.xml
      │     └─ log4j2.xml
      │
      └─ test/
         ├─ java/
         │  └─ com/example/project/batch/
         │     ├─ product/
         │     ├─ tax/
         │     └─ cleanup/
         └─ resources/
            └─ log4j2-test.xml
```

### 모듈별 역할

| 모듈      | 빌드 형식      | 역할                                                     |
| ------- | ---------- | ------------------------------------------------------ |
| `core`  | WAR        | REST API, 인증, 회원, 수증자, 증여 내역, 시뮬레이션, 금융상품 조회, AI 상담 연동 |
| `batch` | JAR 또는 WAR | 금융상품 수집, 상품 수익률 갱신, 세법 데이터 동기화, 데이터 정리                 |
| 루트 프로젝트 | 빌드 관리      | 공통 라이브러리 버전, Java 버전, 저장소 및 테스트 설정                     |


### core 모듈 주요 설정

```text
RootConfig
 ├─ DataSource
 ├─ MyBatis
 ├─ TransactionManager
 ├─ Service
 └─ Repository/Mapper

ServletConfig
 ├─ Controller
 ├─ ViewResolver
 ├─ Jackson
 ├─ Interceptor
 └─ Static Resource

SecurityConfig
 ├─ SecurityFilterChain
 ├─ PasswordEncoder
 ├─ AuthenticationProvider
 └─ JWT Filter
```

### batch 모듈 주요 설정

```text
BatchRootConfig
 ├─ DataSource
 ├─ TransactionManager
 ├─ JobRepository
 ├─ JobLauncher
 └─ JobExplorer

BatchSchedulerConfig
 ├─ Scheduler
 └─ Job 실행 시간 설정

Batch Job
 ├─ Job
 ├─ Step
 ├─ ItemReader
 ├─ ItemProcessor
 └─ ItemWriter
```

### Gradle 모듈 등록

```gradle
// settings.gradle

rootProject.name = 'project-backend'

include 'core'
include 'batch'
```

### core 모듈 빌드 설정

```gradle
// core/build.gradle

plugins {
    id 'java'
    id 'war'
}

dependencies {
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-webmvc'
    implementation 'org.springframework:spring-jdbc'

    compileOnly 'javax.servlet:javax.servlet-api:4.0.1'

    testImplementation 'org.junit.jupiter:junit-jupiter'
}
```

외부 Tomcat에서 Servlet API를 제공하므로 `javax.servlet-api`는 일반적으로 `compileOnly`로 설정합니다.

### batch 모듈 빌드 설정

```gradle
// batch/build.gradle

plugins {
    id 'java'
}

dependencies {
    implementation project(':core')

    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-jdbc'
    implementation 'org.springframework.batch:spring-batch-core'

    testImplementation 'org.junit.jupiter:junit-jupiter'
}
```

### 모듈 의존 관계

```text
core  ←  batch
```

`batch` 모듈이 `core`의 Domain, Mapper 또는 공통 비즈니스 로직을 사용할 경우 다음과 같이 의존할 수 있습니다.

```gradle
dependencies {
    implementation project(':core')
}
```

다만 `core`가 WAR 전용 웹 모듈이라면 `batch`가 `core` 전체를 의존하는 구조는 결합도가 높아질 수 있습니다.

두 모듈만 유지해야 한다면 다음 기준을 적용합니다.

* `core`의 Controller와 Servlet 관련 클래스는 `batch`에서 사용하지 않습니다.
* 공통 Domain과 Mapper는 Servlet API에 의존하지 않도록 작성합니다.
* `batch`가 사용하는 Service는 HTTP 요청 및 응답 객체에 의존하지 않습니다.
* `core`는 `batch`를 의존하지 않습니다.
* 순환 의존성을 만들지 않습니다.

---

## 12. 계층별 역할

### Controller

* HTTP 요청을 수신합니다.
* 요청 DTO의 유효성을 검사합니다.
* Service를 호출합니다.
* 공통 응답 형식으로 결과를 반환합니다.
* 비즈니스 로직을 직접 작성하지 않습니다.

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipients")
public class RecipientController {

    private final RecipientService recipientService;

    @PostMapping
    public ResponseEntity<ApiResponse<RecipientResponse>> create(
            @Valid @RequestBody RecipientCreateRequest request
    ) {
        RecipientResponse response = recipientService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("수증자가 등록되었습니다.", response));
    }
}
```

### Service

* 핵심 비즈니스 로직을 처리합니다.
* 여러 Mapper 또는 외부 API를 조합합니다.
* 트랜잭션 범위를 관리합니다.
* Controller 객체에 직접 의존하지 않습니다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipientServiceImpl implements RecipientService {

    private final RecipientMapper recipientMapper;

    @Override
    @Transactional
    public RecipientResponse create(RecipientCreateRequest request) {
        RecipientVO recipient = request.toEntity();

        recipientMapper.insert(recipient);

        return RecipientResponse.from(recipient);
    }
}
```

### Mapper

* 데이터베이스 접근만 담당합니다.
* 복잡한 비즈니스 로직을 작성하지 않습니다.
* Mapper 인터페이스와 XML의 namespace를 일치시킵니다.

```java
@Mapper
public interface RecipientMapper {

    int insert(RecipientVO recipient);

    RecipientVO findById(Long recipientId);

    List<RecipientVO> findAllByUserId(Long userId);

    int update(RecipientVO recipient);

    int deleteById(Long recipientId);
}
```

---

## 13. MyBatis 작성 규칙

### Mapper 인터페이스

```java
@Mapper
public interface UserMapper {

    UserVO findById(Long userId);

    UserVO findByEmail(String email);

    int insert(UserVO user);

    int update(UserVO user);

    int deleteById(Long userId);
}
```

### Mapper XML

```xml
<?xml version="1.0" encoding="UTF-8"?>

<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.project.user.mapper.UserMapper">

    <resultMap id="userResultMap"
               type="com.example.project.user.domain.UserVO">
        <id property="userId" column="user_id"/>
        <result property="email" column="email"/>
        <result property="password" column="password"/>
        <result property="name" column="name"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <select id="findById"
            parameterType="long"
            resultMap="userResultMap">
        SELECT user_id,
               email,
               password,
               name,
               created_at,
               updated_at
        FROM users
        WHERE user_id = #{userId}
    </select>

</mapper>
```

### MyBatis 규칙

* Mapper 인터페이스와 XML의 `namespace`를 일치시킵니다.
* 메서드명과 SQL 태그의 `id`를 일치시킵니다.
* `SELECT *` 사용을 지양합니다.
* 복잡한 결과 매핑에는 `resultMap`을 사용합니다.
* 파라미터가 여러 개인 경우 `@Param`을 사용합니다.
* 동적 SQL이 필요한 경우 `<if>`, `<choose>`, `<where>`를 사용합니다.
* SQL 키워드는 대문자로 작성하는 것을 권장합니다.
* 테이블명과 컬럼명은 `snake_case`를 사용합니다.

---

## 14. 데이터베이스 규칙

### 명명 규칙

| 구분    | 규칙               | 예시                          |
| ----- | ---------------- | --------------------------- |
| 테이블   | 복수형 `snake_case` | `users`, `gift_histories`   |
| 컬럼    | `snake_case`     | `user_id`, `created_at`     |
| 기본키   | `{단수 테이블명}_id`   | `user_id`                   |
| 외래키   | 참조 테이블 기본키명      | `recipient_id`              |
| 인덱스   | `idx_테이블_컬럼`     | `idx_users_email`           |
| 유니크 키 | `uk_테이블_컬럼`      | `uk_users_email`            |

### 공통 컬럼 예시

```sql
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP
```

### 데이터베이스 변경 규칙

* 테이블 변경 전 팀원에게 공유합니다.
* 운영 데이터베이스를 직접 수정하지 않습니다.
* 컬럼 삭제 또는 타입 변경 시 영향 범위를 확인합니다.
* DDL 변경 내역을 별도 SQL 파일로 관리합니다.
* 초기 데이터는 DML 파일로 분리합니다.

```text
database/
├─ ddl/
│  ├─ V001__create_users.sql
│  ├─ V002__create_recipients.sql
│  └─ V003__create_gift_histories.sql
└─ dml/
   └─ test_data.sql
```

---

## 15. 개발 환경 설정

### 필수 설치 항목

* Java
* IntelliJ IDEA
* MySQL
* Git
* Gradle 또는 Maven
* Postman

### 권장 버전

```text
Java: 17 이상
MySQL: 8.0 이상
Gradle: 프로젝트 Wrapper 버전 사용
```

설치된 버전은 다음 명령어로 확인합니다.

```bash
java -version
git --version
mysql --version
```

Gradle 버전 확인:

```bash
./gradlew --version
```

Windows에서는 다음 명령어를 사용할 수 있습니다.

```bash
gradlew.bat --version
```

---

## 16. 프로젝트 설치 및 실행

### 저장소 복제

```bash
git clone 저장소_URL
```

### 프로젝트 디렉터리 이동

```bash
cd 프로젝트_디렉터리
```

### 설정 파일 생성

다음 설정 파일을 생성하거나 샘플 파일을 복사합니다.

```text
src/main/resources/application-local.yml
```

### 프로젝트 빌드

macOS 또는 Linux:

```bash
./gradlew clean build
```

Windows:

```bash
gradlew.bat clean build
```

### 테스트 제외 빌드

```bash
./gradlew clean build -x test
```

Windows:

```bash
gradlew.bat clean build -x test
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

Spring Legacy 프로젝트라면 Tomcat에 WAR 파일을 배포하여 실행합니다.

### 실행 확인

```text
http://localhost:8080
```

Health Check API를 구현한 경우:

```text
http://localhost:8080/api/health
```

---

## 17. 환경 변수 설정

민감한 정보는 코드 또는 Git 저장소에 직접 작성하지 않습니다.

### 환경 변수 예시

```env
DB_URL=jdbc:mysql://localhost:3306/project_db
DB_USERNAME=root
DB_PASSWORD=password

JWT_SECRET=JWT_SECRET_KEY
JWT_ACCESS_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=1209600000

REDIS_HOST=localhost
REDIS_PORT=6379

FASTAPI_BASE_URL=http://localhost:8000
OPENAI_API_KEY=OPENAI_API_KEY
```

### application-local.yml 예시

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}

mybatis:
  mapper-locations: classpath:/mapper/**/*.xml
  type-aliases-package: com.example.project
  configuration:
    map-underscore-to-camel-case: true

jwt:
  secret: ${JWT_SECRET}
  access-expiration: ${JWT_ACCESS_EXPIRATION}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION}

external:
  fastapi:
    base-url: ${FASTAPI_BASE_URL}
```

### `.gitignore` 예시

```gitignore
.env
.env.*
!/.env.example

application-local.yml
application-secret.yml

.gradle/
build/
out/
.idea/
*.iml
```

### `.env.example`

```env
DB_URL=
DB_USERNAME=
DB_PASSWORD=

JWT_SECRET=
JWT_ACCESS_EXPIRATION=
JWT_REFRESH_EXPIRATION=

REDIS_HOST=
REDIS_PORT=

FASTAPI_BASE_URL=
OPENAI_API_KEY=
```

---

## 18. 실행 프로파일

| 프로파일    | 설명          |
| ------- | ----------- |
| `local` | 개인 로컬 개발 환경 |
| `dev`   | 팀 공용 개발 서버  |
| `test`  | 자동화 테스트 환경  |
| `prod`  | 운영 서버 환경    |

프로파일 실행 예시:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Windows:

```bash
gradlew.bat bootRun --args="--spring.profiles.active=local"
```

환경 변수 방식:

```bash
SPRING_PROFILES_ACTIVE=local
```

---

## 19. 데이터베이스 설정

### 데이터베이스 생성

```sql
CREATE DATABASE project_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

### 사용자 생성 예시

```sql
CREATE USER 'project_user'@'localhost'
IDENTIFIED BY 'password';
```

### 권한 부여

```sql
GRANT ALL PRIVILEGES
ON project_db.*
TO 'project_user'@'localhost';

FLUSH PRIVILEGES;
```

### 연결 정보

```text
Host: localhost
Port: 3306
Database: project_db
Username: project_user
Password: 개인 환경에 맞게 설정
```

---

## 20. 인증 및 인가

### 인증 방식

JWT 기반 인증을 사용합니다.

```text
로그인 요청
    ↓
사용자 인증
    ↓
Access Token 및 Refresh Token 발급
    ↓
클라이언트에서 Access Token 전달
    ↓
JWT 인증 필터에서 토큰 검증
    ↓
SecurityContext에 인증 정보 등록
    ↓
Controller 접근 허용
```

### Authorization 헤더

```http
Authorization: Bearer {accessToken}
```

### 권한 구분 예시

| 권한               | 설명         |
| ---------------- | ---------- |
| `ROLE_USER`      | 일반 사용자     |
| `ROLE_ADMIN`     | 관리자        |
| `ROLE_COUNSELOR` | 은행원 또는 상담사 |

### 접근 권한 예시

| 경로                    | 접근 권한        |
| --------------------- | ------------ |
| `/api/auth/**`        | 전체 허용        |
| `/api/products/**`    | 전체 또는 인증 사용자 |
| `/api/users/**`       | 인증 사용자        |
| `/api/recipients/**`  | 인증 사용자       |
| `/api/simulations/**` | 인증 사용자       |
| `/api/admin/**`       | 관리자          |

---

## 21. 예외 처리

전역 예외 처리기를 통해 API 오류 응답 형식을 통일합니다.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }
}
```

### 주요 예외

| 예외                         | 설명           |
| -------------------------- | ------------ |
| `BusinessException`        | 비즈니스 규칙 위반   |
| `EntityNotFoundException`  | 조회한 데이터가 없음  |
| `DuplicateUserException`   | 회원 이메일 중복    |
| `UnauthorizedException`    | 인증 실패        |
| `ForbiddenException`       | 접근 권한 없음     |
| `ExternalApiException`     | 외부 API 연동 실패 |
| `SimulationException`      | 시뮬레이션 계산 실패  |

### 예외 처리 규칙

* Controller에서 반복적인 `try-catch`를 작성하지 않습니다.
* 예외 메시지에 비밀번호나 토큰을 포함하지 않습니다.
* 사용자에게 내부 SQL 오류를 그대로 반환하지 않습니다.
* 예상 가능한 오류는 비즈니스 예외로 변환합니다.
* 처리되지 않은 예외는 로그에 상세 내용을 기록합니다.

---

## 22. 로깅 규칙

### 로그 레벨

| 레벨      | 사용 기준                  |
| ------- | ---------------------- |
| `ERROR` | 시스템 오류 및 처리 실패         |
| `WARN`  | 즉시 장애는 아니지만 확인이 필요한 상황 |
| `INFO`  | 주요 비즈니스 처리 및 서버 상태     |
| `DEBUG` | 개발 단계의 상세 정보           |
| `TRACE` | 매우 세부적인 흐름 추적          |

### 로그 작성 예시

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationServiceImpl implements SimulationService {

    @Override
    public SimulationResponse simulate(
            SimulationRequest request
    ) {
        log.info(
                "증여 시뮬레이션 시작. recipientId={}, giftAmount={}",
                request.getRecipientId(),
                request.getGiftAmount()
        );

        return new SimulationResponse();
    }
}
```

### 로그 작성 시 주의사항

* 비밀번호를 로그에 남기지 않습니다.
* JWT 원문을 로그에 남기지 않습니다.
* 주민등록번호와 같은 개인정보를 남기지 않습니다.
* 객체 전체를 무분별하게 출력하지 않습니다.
* 오류 로그에는 원인과 요청 식별 정보를 포함합니다.

---

## 23. 외부 API 연동

### FastAPI 연동 구조

```text
Spring Backend
    │
    ├─ 사용자 질문 및 상담 정보 전달
    ▼
FastAPI
    │
    ├─ Vector Database 검색
    ├─ OpenAI API 호출
    └─ 답변 및 출처 생성
    │
    ▼
Spring Backend
    │
    ├─ 상담 내역 저장
    └─ Frontend에 응답
```

### FastAPI 요청 예시

```json
{
  "userId": 1,
  "question": "미성년 자녀에게 증여할 때 공제 한도는 얼마인가요?",
  "conversationId": "conversation-001"
}
```

### FastAPI 응답 예시

```json
{
  "answer": "관련 법령과 사용자의 상황을 바탕으로 생성한 답변입니다.",
  "sources": [
    {
      "title": "상속세 및 증여세법",
      "reference": "관련 조항"
    }
  ]
}
```

### 외부 API 연동 규칙

* 연결 시간 제한을 설정합니다.
* 응답 시간 제한을 설정합니다.
* 연동 실패 시 사용자 정의 예외로 변환합니다.
* 재시도가 가능한 오류와 불가능한 오류를 구분합니다.
* API 키를 코드에 직접 작성하지 않습니다.
* 외부 서버의 응답을 그대로 클라이언트에 반환하지 않습니다.

---

## 24. Spring Batch 구성

### 배치 작업 예시

| Job                       | 실행 주기 | 설명               |
| ------------------------- | ----- | ---------------- |
| `financialProductSyncJob` | 매일    | 금융상품 데이터 갱신      |
| `productReturnUpdateJob`  | 매일    | 금융상품 수익률 갱신      |
| `taxLawSyncJob`           | 매주    | 세법 및 공제 기준 업데이트  |
| `expiredTokenCleanupJob`  | 매일    | 만료된 토큰 데이터 삭제    |
| `consultationCleanupJob`  | 매월    | 오래된 임시 상담 데이터 정리 |

### 배치 처리 구조

```text
Job
 └─ Step
     ├─ ItemReader
     ├─ ItemProcessor
     └─ ItemWriter
```

### 배치 작성 규칙

* Job과 Step 이름을 중복되지 않게 작성합니다.
* 배치 실행 결과를 기록합니다.
* 실패한 항목의 재처리 방법을 정의합니다.
* 대량 데이터는 Chunk 방식으로 처리합니다.
* 외부 API 호출 제한을 고려합니다.
* 동일 데이터가 중복 저장되지 않도록 처리합니다.

---

## 25. 테스트

### 테스트 종류

| 테스트            | 설명                      |
| -------------- | ----------------------- |
| 단위 테스트         | Service, 계산 로직 단위 검증    |
| Mapper 테스트     | MyBatis SQL 및 매핑 검증     |
| Controller 테스트 | 요청, 응답, 유효성 검사          |
| 통합 테스트         | Controller부터 DB까지 전체 흐름 |
| 외부 연동 테스트      | FastAPI 및 외부 API 연동     |
| 배치 테스트         | Job 및 Step 실행 검증        |

### 전체 테스트 실행

```bash
./gradlew test
```

Windows:

```bash
gradlew.bat test
```

### 특정 테스트 실행

```bash
./gradlew test --tests "com.example.project.simulation.SimulationServiceTest"
```

### 테스트 작성 예시

```java
@ExtendWith(MockitoExtension.class)
class RecipientServiceTest {

    @Mock
    private RecipientMapper recipientMapper;

    @InjectMocks
    private RecipientServiceImpl recipientService;

    @Test
    @DisplayName("수증자를 등록할 수 있다")
    void createRecipient() {
        // given

        // when

        // then
    }
}
```

### 테스트 규칙

* 테스트 메서드의 목적을 명확하게 작성합니다.
* 성공 사례와 실패 사례를 모두 작성합니다.
* 테스트 간 실행 순서에 의존하지 않습니다.
* 외부 API는 가능한 경우 Mock으로 대체합니다.
* 세금 계산 로직은 경계값 테스트를 포함합니다.

---

## 26. 코드 작성 규칙

### 클래스 이름

PascalCase를 사용합니다.

```text
UserController
UserService
UserServiceImpl
UserMapper
UserVO
UserResponse
```

### 변수와 메서드

camelCase를 사용합니다.

```java
Long userId;
String userName;

getUser();
createRecipient();
calculateGiftTax();
```

### 상수

대문자와 언더스코어를 사용합니다.

```java
private static final int MAX_RETRY_COUNT = 3;
private static final long ACCESS_TOKEN_EXPIRATION = 3600000L;
```

### Boolean 변수

`is`, `has`, `can`, `should`와 같은 접두사를 사용합니다.

```java
boolean isDeleted;
boolean hasPermission;
boolean canReissueToken;
```

### DTO 이름

| 목적    | 접미사 예시            |
| ----- | ----------------- |
| 생성 요청 | `CreateRequest`   |
| 수정 요청 | `UpdateRequest`   |
| 조회 응답 | `Response`        |
| 목록 응답 | `SummaryResponse` |
| 검색 조건 | `SearchCondition` |

예시:

```text
UserSignupRequest
UserUpdateRequest
UserResponse
UserSummaryResponse
GiftSearchCondition
```

---

## 27. Git 브랜치 전략

| 브랜치          | 설명           |
| ------------ | ------------ |
| `main`       | 배포 가능한 안정 버전 |
| `develop`    | 개발 기능 통합 브랜치 |
| `feature/*`  | 신규 기능 개발     |
| `fix/*`      | 일반 오류 수정     |
| `hotfix/*`   | 운영 환경 긴급 수정  |
| `refactor/*` | 구조 및 코드 개선   |

브랜치명에는 Jira 티켓 번호와 작업명을 포함합니다.

```text
feature/KAN-30_user-signup
feature/KAN-41_gift-simulation
feature/KAN-52_fastapi-client
fix/KAN-63_jwt-authentication
refactor/KAN-71_common-response
```

### 작업 브랜치 생성

```bash
git checkout develop
git pull origin develop
git checkout -b feature/KAN-30_user-signup
```

### 작업 완료 후 Push

```bash
git add .
git commit -m "KAN-30 feat: 회원가입 API 구현"
git push origin feature/KAN-30_user-signup
```

`main`과 `develop` 브랜치에는 직접 Push하지 않습니다.

---

## 28. 커밋 메시지 규칙

커밋 메시지는 다음 형식을 사용합니다.

```text
Jira티켓번호 타입: 작업 내용
```

예시:

```text
KAN-30 feat: 회원가입 API 구현
KAN-31 fix: 이메일 중복 검사 오류 수정
KAN-32 refactor: 회원 조회 로직 분리
KAN-33 test: 회원가입 서비스 테스트 추가
KAN-34 docs: API 명세 수정
```

### 커밋 타입

| 타입         | 설명             |
| ---------- | -------------- |
| `feat`     | 새로운 기능 추가      |
| `fix`      | 오류 수정          |
| `refactor` | 기능 변경 없는 코드 개선 |
| `test`     | 테스트 코드 추가 및 수정 |
| `docs`     | 문서 수정          |
| `chore`    | 환경 설정 및 의존성 변경 |
| `build`    | 빌드 관련 변경       |
| `perf`     | 성능 개선          |
| `rename`   | 파일명 또는 경로 변경   |
| `remove`   | 코드 또는 파일 삭제    |

---

## 29. Pull Request 규칙

Pull Request는 기능 단위로 작성합니다.

### PR 제목

```text
[KAN-30] 회원가입 API 구현
```

### PR 본문 예시

```md
## 작업 내용

- 회원가입 API 구현
- 이메일 중복 검사 구현
- 비밀번호 암호화 적용
- 회원가입 요청 유효성 검사 추가

## API 변경 사항

- POST /api/auth/signup

## 데이터베이스 변경 사항

- users 테이블 사용
- email 컬럼 UNIQUE 제약조건 적용

## 테스트 내용

- 정상 회원가입 확인
- 이메일 중복 요청 확인
- 잘못된 이메일 형식 확인
- 비밀번호 규칙 위반 확인

## 관련 티켓

- KAN-30

## 참고 사항

- 로컬 MySQL 실행이 필요합니다.
```

### PR 확인 사항

* 프로젝트 빌드 성공 여부
* 테스트 코드 통과 여부
* 데이터베이스 변경 여부
* API 명세 변경 여부
* 환경 변수 추가 여부
* 민감 정보 포함 여부
* 불필요한 로그 포함 여부
* 사용하지 않는 import 제거 여부
* 트랜잭션 적용 범위 확인
* 관련 Jira 티켓 연결 여부

---

## 30. 협업 절차

1. Jira 티켓을 확인합니다.
2. GitHub Issue를 생성하거나 연결합니다.
3. 최신 `develop` 브랜치를 Pull합니다.
4. Jira 티켓 번호를 포함한 작업 브랜치를 생성합니다.
5. 기능을 구현합니다.
6. 단위 테스트 및 API 테스트를 진행합니다.
7. 데이터베이스 변경 사항을 문서화합니다.
8. 커밋 후 원격 저장소에 Push합니다.
9. `develop` 브랜치를 대상으로 Pull Request를 생성합니다.
10. 코드 리뷰 내용을 반영합니다.
11. 승인 후 병합합니다.
12. Jira 티켓 상태를 완료로 변경합니다.

---

## 31. 개발 시 주의사항

* `main`, `develop` 브랜치에 직접 Push하지 않습니다.
* Controller에 비즈니스 로직을 작성하지 않습니다.
* Service에서 HTTP 요청 및 응답 객체를 직접 사용하지 않습니다.
* SQL을 Service 또는 Controller에 직접 작성하지 않습니다.
* API Key, 비밀번호, JWT Secret을 커밋하지 않습니다.
* Entity 또는 VO를 API 응답으로 직접 반환하지 않습니다.
* 예외 메시지에 내부 시스템 정보를 노출하지 않습니다.
* 개인정보를 로그에 남기지 않습니다.
* 외부 API 주소를 코드에 직접 작성하지 않습니다.
* 새로운 환경 변수를 추가하면 `.env.example`도 수정합니다.
* 데이터베이스 구조를 변경하면 팀원에게 공유합니다.
* 작업 완료 전 빌드와 테스트를 실행합니다.

---

## 32. TODO

### 공통

* [ ] 프로젝트 초기 설정
* [ ] MySQL 연결
* [ ] MyBatis 설정
* [ ] 공통 API 응답 구현
* [ ] 전역 예외 처리 구현
* [ ] 요청 유효성 검사 설정
* [ ] CORS 설정
* [ ] 로깅 설정
* [ ] Swagger 또는 API 문서 설정

### 인증 및 회원

* [ ] 회원가입 API
* [ ] 로그인 API
* [ ] 비밀번호 암호화
* [ ] JWT 발급
* [ ] JWT 인증 필터
* [ ] Refresh Token 관리
* [ ] 로그아웃
* [ ] 회원 정보 조회
* [ ] 회원 정보 수정
* [ ] 회원 탈퇴

### 수증자 및 증여 내역

* [ ] 수증자 등록
* [ ] 수증자 목록 조회
* [ ] 수증자 상세 조회
* [ ] 수증자 수정
* [ ] 수증자 삭제
* [ ] 증여 내역 등록
* [ ] 증여 내역 조회
* [ ] 누적 증여액 계산
* [ ] 공제 기준일 계산

### 증여 시뮬레이션

* [ ] 증여 공제 한도 계산
* [ ] 과세표준 계산
* [ ] 증여세 계산
* [ ] 즉시 증여 시나리오
* [ ] 분할 증여 시나리오
* [ ] 금융상품 수익 계산
* [ ] 시나리오 비교
* [ ] 시뮬레이션 결과 저장

### 금융상품

* [ ] 금융상품 목록 조회
* [ ] 금융상품 상세 조회
* [ ] 상품 유형별 검색
* [ ] 수익률 데이터 저장
* [ ] 안정성 정보 저장
* [ ] 상품 비교 기능

### AI 상담

* [ ] FastAPI 연결 설정
* [ ] 상담 질문 전달
* [ ] 상담 답변 수신
* [ ] 상담 내역 저장
* [ ] 상담 내역 조회
* [ ] 관련 법령 출처 저장
* [ ] 외부 서버 오류 처리

### Spring Batch

* [ ] Batch 기본 설정
* [ ] 금융상품 수집 Job
* [ ] 금융상품 수익률 갱신 Job
* [ ] 세법 데이터 갱신 Job
* [ ] 배치 실행 이력 관리
* [ ] 배치 실패 재처리

---

## 33. 알려진 문제

| 문제                      | 원인             | 상태       |
| ----------------------- | -------------- | -------- |
| FastAPI 서버 미실행 시 상담 실패  | 외부 서버 연결 불가    | 예외 처리 예정 |
| 로컬 환경별 DB 설정 차이         | 환경 설정 파일 미분리   | 수정 중     |
| Refresh Token 저장 방식 미확정 | 인증 정책 논의 중     | 검토 중     |
| 배치 중복 데이터 저장 가능성        | 중복 검사 미구현      | 수정 예정    |
| 증여세 계산 기준 변경 가능성        | 세법 데이터 자동화 미구현 | 검토 중     |

---

## 34. 팀원 및 담당 기능

| 이름  | 담당 기능                          |
| --- | ------------------------------ |
| 강소연 | 증여 시뮬레이션 기능                    |
| 권규민 | 회원 관리 기능 및 데이터베이스 구축           |
| 김윤정 | 증여 시뮬레이션 기능                    |
| 이세형 | AI 상담 기능, FastAPI 서버 구축, 공통 기능 |
| 전소현 | AI 상담 기능 및 FastAPI 서버 구축       |
| 전재현 | Spring Batch, 증여 현황 관리 기능      |

담당 기능은 개발 상황에 따라 변경될 수 있습니다.

---

## 35. 관련 문서

* API 명세서: `링크 입력`
* ERD: `링크 입력`
* Jira 프로젝트: `링크 입력`
* Notion 문서: `링크 입력`
* Frontend 저장소: `링크 입력`
* FastAPI 저장소: `링크 입력`
* Swagger: `링크 입력`
* 개발 서버 주소: `링크 입력`
* 배포 주소: 개발 완료 후 추가 예정

---

## 36. 문의

프로젝트 관련 문의는 GitHub Issue 또는 프로젝트 협업 채널을 이용합니다.
