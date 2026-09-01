# MiriZoom (미리줌)

<div align="center">
  <img width="357" height="316" alt="Image" src="https://github.com/user-attachments/assets/3d3df4f7-c86b-4b72-b3cb-dc5c5a20eb1e" />

  **미리 준비하는 다음 10년, 증여**

  자녀를 위한 증여 계획부터 세금 계산, 금융상품 비교, 일정 관리, AI 세법 상담까지 한 번에 제공하는 디지털 증여 관리 서비스입니다.
</div>

<p align="center">
  <img src="https://img.shields.io/badge/Vue.js-3.5-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue.js" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring-5.3-6DB33F?logo=spring&logoColor=white" alt="Spring 5.3" />
  <img src="https://img.shields.io/badge/FastAPI-0.115+-009688?logo=fastapi&logoColor=white" alt="FastAPI" />
  <img src="https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/AWS-EC2%20%7C%20RDS-FF9900?logo=amazonwebservices&logoColor=white" alt="AWS" />
</p>

---

## 목차

- [서비스 소개](#서비스-소개)
- [핵심 기능](#핵심-기능)
- [팀원 및 담당 기능](#팀원-및-담당-기능)
- [시스템 아키텍처](#시스템-아키텍처)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [로컬 실행](#로컬-실행)
- [주요 API](#주요-api)
- [협업 방식](#협업-방식)

## 서비스 소개

MiriZoom은 증여를 계획하는 부모가 가족관계와 과거 증여 이력을 바탕으로 증여세를 계산하고, 즉시 증여와 분할 증여의 장기 결과를 비교할 수 있도록 돕는 서비스입니다. 계산 결과를 예금·적금·ETF 포트폴리오와 연결하고, 저장한 계획을 실제 증여 일정과 신고 준비로 이어갈 수 있도록 설계했습니다.

### 해결하고자 한 문제

- 부동산 등 비유동 자산 중심의 이전 계획으로 세금 납부 재원이 부족해지는 문제
- 공제 한도와 누진세율을 정확히 반영하지 못해 예상보다 많은 증여세가 발생하는 문제
- 증여 계획, 금융상품 선택, 신고 일정과 증빙 서류가 서로 분리되어 관리되는 불편
- 개인의 가족관계와 과거 이력을 반영한 세법 정보를 찾기 어려운 문제

### 주요 사용자

- 자녀에게 자산을 이전하려는 40~50대 부모
- 자녀별 과거 증여 내역과 남은 공제 한도를 관리하려는 사용자
- 증여 이후의 장기 운용 결과까지 비교하고 싶은 사용자
- 증여세와 절세 방법을 대화형으로 확인하고 싶은 사용자

### 개발 기간

2026년 7월 ~ 2026년 8월

## 핵심 기능

### 1. 증여 시뮬레이션

<div align="center">
  <img width="720" height="475" alt="Image" src="https://github.com/user-attachments/assets/d3206275-7b13-4e87-ad2f-215c49b12728" />
</div>

> 담당: [강소연](https://github.com/wosyh18), [김윤정](https://github.com/jyk3716)

가족관계, 증여 예정일, 증여 금액, 운용 기간과 세금 납부 주체를 입력하면 과거 10년 증여 이력을 반영해 여러 증여 전략을 계산합니다.

- 수증자의 연령과 관계에 따른 10년 단위 증여재산공제 계산
- 과세표준, 누진세율, 누진공제액과 신고세액공제를 반영한 예상 세액 산출
- 수증자 납부와 증여자 대납에 따른 운용 원금 및 총 준비 금액 비교
- 즉시 증여와 분할 증여 시나리오 생성 및 동일 평가일 기준 예상 자산 비교
- 안정형·균형형·성장형 포트폴리오와 예금·적금·ETF 상품 추천
- 상품 만기 후 재가입, 회차별 금리, ETF 연환산 수익률과 변동성을 반영한 미래가치 계산
- 시뮬레이션 임시 저장, 최종 저장, 수증자별 이력 조회 및 재계산
- 세법 경계값, 공제 갱신일, 동일 일자 증여, 미성년자에서 성년자로 전환되는 경우 등 예외 처리

### 2. 증여 현황 및 일정 관리

> 담당: [전재현](https://github.com/JaeHyun154)

저장한 시뮬레이션을 실제 증여 계획으로 전환하고, 수증자별 이력과 공제 한도, 신고 일정을 한 화면에서 관리합니다.

- 수증자별 예정·진행·완료 증여 내역 조회 및 상태 변경
- 과거 10년 증여액과 남은 공제 한도, 공제 갱신일 표시
- 분할 증여 회차와 다음 증여일 자동 갱신
- 신고·납부 기한 리마인더와 읽음 상태 관리
- 가족관계증명서, 이체확인증, 증여세 신고서 등 필수 증빙 가이드
- CLOVA OCR을 이용한 증여세 신고서 인식 및 신고 정보 자동 반영

### 3. AI 세법 상담

> 담당: [이세형](https://github.com/hyeongls), [전소현](https://github.com/ssohy)

Spring 서버가 회원의 가족·상품 정보를 안전하게 조합해 FastAPI로 전달하고, FastAPI는 법령 및 국세청 해석 자료를 검색해 OpenAI 기반 답변을 생성합니다.

- 질문 의도 분류, 한국어 금액·날짜·관계 정보 정규화
- 답변에 필요한 정보가 부족할 때 추가 질문을 생성하고 상담을 이어가는 clarification 흐름
- 국가법령정보센터 법령과 국세청 해석 데이터를 ChromaDB에 적재한 RAG 검색
- 사용자 가족관계, 과거 증여 및 금융상품 정보를 반영한 개인화 답변
- 답변 근거가 된 법령·조문 링크 제공 및 상담 내역 조회
- FAQ 카테고리별 조회와 자주 묻는 질문 바로가기
- 서비스 범위를 벗어난 질문과 프롬프트 탈옥 시도 차단 및 관리자 신고 연계
- Spring WebClient 비동기 통신과 FastAPI 예외 응답 표준화

### 4. 회원·가족 및 인증 관리

> 담당: [권규민](https://github.com/gumin00)

- 이메일 중복 확인, 회원가입, 로그인, 로그아웃과 토큰 재발급
- Spring Security와 JWT Access/Refresh Token 기반 인증
- 내 정보 조회·수정, 프로필 이미지 관리와 회원 탈퇴
- 수증자 등록·조회·수정·삭제 및 사용자 소유권 검증
- 회원 탈퇴 시 연관 데이터 정리
- 개인정보 암호화와 검색용 HMAC 처리
- 차단 회원의 AI 상담·시뮬레이션 접근 제한

### 5. 관리자 운영 기능

> 담당: [권규민](https://github.com/gumin00), [전소현](https://github.com/ssohy), [이세형](https://github.com/hyeongls), [전재현](https://github.com/JaeHyun154)

- **대시보드·회원 관리**: 가입자 추이, 시뮬레이션 실행·저장 지표, 회원 조회·차단·해제·삭제
- **상품 관리**: 데이터 버전 생성, 예금·적금·ETF 등록·수정, 적재 완료 처리와 버전 삭제
- **FAQ·신고 관리**: FAQ와 카테고리 CRUD, AI 안전 신고 조회 및 처리
- **권한 관리**: 관리자 생성, 역할 변경·해제와 권한별 접근 제어
- **감사·접근 로그**: 관리자 작업 이력 조회, SSE 기반 관리자 접근 로그 확인
- **배치 운영**: 배치 작업·실행 이력 조회, 수동 실행, 실패 지점부터 재시작
- **운영 알림**: 배치 실패 등 관리자 알림의 읽음·해결 상태 관리

### 6. 오프라인 상담 연결

> 담당: [전소현](https://github.com/ssohy)

- 카카오 로컬 API를 이용한 주변 KB국민은행 지점·세무서 검색
- 등록된 지점 정보 조회와 상담 번호표 발급
- 대기 인원, 최근 호출 번호와 호출 상태 확인

## 팀원 및 담당 기능

| 사진 | 팀원 | GitHub | 주요 담당 | 구현 기여 |
|:---:|:---:|:---:|---|---|
| <img width="100" height="100" alt="Image" src="https://github.com/user-attachments/assets/8a1018b8-2549-45e2-ac9b-781ca147f049" /> | **이세형** · 팀장 | [@hyeongls](https://github.com/hyeongls) | 프로젝트 관리, AI 상담, 관리자 감사 | FastAPI RAG 구조와 상담 파이프라인, 법령 근거 링크, Spring 연동, AI 대화 데드락 개선, 관리자 감사 로그 |
| <img width="100" height="100" alt="Image" src="https://github.com/user-attachments/assets/3b8b243c-3d28-42f3-b000-c5ad7026a21a" /> | **강소연** | [@wosyh18](https://github.com/wosyh18) | 증여 시뮬레이션, 인프라·CI/CD | 시뮬레이션 조회·저장과 세액·분할 증여 로직, 상품 변동성, Jenkins·Docker Hub·EC2 배포 자동화, 배치 배포 |
| <img width="100" height="100" alt="image" src="https://github.com/user-attachments/assets/2c91b0a6-fd77-4a6b-8872-7f4f88565b76" /> | **권규민** | [@gumin00](https://github.com/gumin00) | 회원·인증, 개인정보 보호, 관리자 회원 | JWT 인증, 회원·프로필·수증자 관리, 회원 차단·탈퇴, 개인정보 암호화, 관리자 대시보드와 회원 관리 UI·API |
| <img width="100" height="100" alt="Image" src="https://github.com/user-attachments/assets/2a704075-d626-4c33-8aaf-6984dcdfcb6a" /> | **김윤정** | [@jyk3716](https://github.com/jyk3716) | 증여 시뮬레이션 UI·API·DB | 시뮬레이션 핵심 계산 API, 공제·세율 경계 로직, 상품 배분과 미래가치 계산, 시나리오·포트폴리오 UI |
| <img width="100" height="100" alt="Image" src="https://github.com/user-attachments/assets/e843a2b6-20af-44b5-9603-510b86e0dc03" /> | **전소현** | [@ssohy](https://github.com/ssohy) | AI 상담 UI, 관리자 상품, 오프라인 상담 | 상담·FAQ UI, Spring-FastAPI WebClient 연동, 상품 컨텍스트 전달, 관리자 상품 버전 관리, 지점 검색·번호표 |
| <img width="100" height="100" alt="Image" src="https://github.com/user-attachments/assets/1ce9e292-a9b6-4b72-be31-02c700f2a74c" /> | **전재현** | [@JaeHyun154](https://github.com/JaeHyun154) | 증여 현황, 세법 배치, 알림 | 수증자·증여 API, 공제 및 신고 정보, 증여 현황 UI, 리마인더, CLOVA OCR, 법령 배치, 배치 실패 알림·운영 화면 |

## 시스템 아키텍처
<img width="1617" height="1016" alt="Image" src="https://github.com/user-attachments/assets/2e3f02dc-cdfa-46a7-b059-ece084ba52b8" />

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | Vue 3, JavaScript, Vite, Pinia, Vue Router, HTML5, CSS3 |
| Backend | Java 17, Spring Framework 5.3, Spring MVC, Spring Security, Spring Batch, MyBatis, WebClient, Caffeine |
| AI | Python 3.12, FastAPI, OpenAI API, ChromaDB, Pydantic, BeautifulSoup, HTTPX |
| Database | MySQL, Amazon RDS |
| External API | CLOVA OCR, 카카오 로컬 API, 국가법령정보센터·국세청 관련 데이터 |
| Infra | AWS EC2, VPC, Nginx, Docker, Docker Compose, Docker Hub, Jenkins |
| Collaboration | GitHub, Jira, Notion, Slack, Figma, Postman |
| Test | JUnit 5, Spring Test, pytest |

## 프로젝트 구조

MiriZoom은 역할이 분리된 네 개의 저장소를 한 디렉터리에서 함께 실행하는 구조입니다.

```text
KB_PTJ/
├── frontend/                 # Vue 3 사용자·관리자 웹 애플리케이션
├── backend/                  # Java 17 / Spring Legacy 멀티 모듈
│   ├── core/                 # REST API, 인증, 핵심 비즈니스 로직
│   └── batch/                # 법령·상품 데이터 배치와 운영 작업
├── FastApi/                  # OpenAI + ChromaDB 기반 AI 상담 서버
├── deploy/                   # Docker Compose, Nginx, Jenkins 배포 구성
└── docs/images/              # 통합 README 이미지
```

### 저장소

| 구성요소 | 저장소 | 설명 |
|---|---|---|
| Frontend | [frontend](https://github.com/KB-IT-s-your-life-PJT-23-4/frontend) | 사용자·관리자 UI와 API 연동 |
| Backend | [backend](https://github.com/KB-IT-s-your-life-PJT-23-4/backend) | Spring MVC API와 Spring Batch |
| AI Server | [FastApi](https://github.com/KB-IT-s-your-life-PJT-23-4/FastApi) | RAG 기반 AI 세법 상담 |
| Deploy | [deploy](https://github.com/KB-IT-s-your-life-PJT-23-4/deploy) | 컨테이너 실행과 배포 자동화 |

## 로컬 실행

### 통합 실행

#### 요구 사항

- Docker 및 Docker Compose
- MySQL 8 호환 데이터베이스
- OpenAI API Key
- 기능별로 카카오 로컬 API, CLOVA OCR Key 등 추가 환경 변수

```bash
cd deploy
cp .env.example .env
```

`.env`에 필요한 값을 입력한 뒤 실행합니다.

```bash
docker compose -f docker-compose-local.yml up --build
```

기본 접속 주소는 `http://localhost`입니다.

> `.env`에는 데이터베이스 비밀번호와 API Key가 포함되므로 Git에 커밋하지 않습니다.

### 개별 실행

#### Frontend

```bash
cd frontend
npm install
npm run dev
```

#### Backend

```bash
cd backend
./gradlew :core:compileJava :batch:compileJava
./gradlew :core:war
```

Spring Legacy WAR 프로젝트이므로 로컬 실행에는 Servlet 4 호환 Tomcat과 `application.properties`, `database.properties` 설정이 필요합니다. 예시는 각 모듈의 `*.example` 파일을 참고합니다.

#### FastAPI

```bash
cd FastApi
uv sync
cp .env.example .env
uv run uvicorn app.main:app --reload
```

## 주요 API

| 영역 | 경로 | 설명 |
|---|---|---|
| 인증 | `/api/auth` | 로그인, 토큰 재발급, 로그아웃 |
| 회원 | `/api/users/me` | 내 정보 조회·수정·탈퇴 |
| 가족 | `/api/fm/family` | 수증자 등록·조회·수정·삭제 |
| 시뮬레이션 | `/api/gs` | 실행, 목록·상세·상품 조회, 최종 저장 |
| 증여 현황 | `/api/gm/gift` | 증여 등록·조회·수정·상태 변경·삭제 |
| OCR | `/api/gm/gift/{giftId}/ocr` | 증여세 신고서 인식 |
| AI 상담 | `/api/ai/consult` | 최초 질문과 추가 질문 처리 |
| FAQ | `/api/ai/faq` | FAQ 목록과 답변 조회 |
| 관리자 | `/api/admin/**` | 회원, 상품, FAQ, 신고, 권한, 감사, 배치, 알림 관리 |
| FastAPI | `/api/v1/chat` | RAG 답변 생성 및 clarification 처리 |

## 협업 방식

- Jira 이슈와 GitHub 브랜치를 연결해 기능 단위로 작업했습니다.
- 브랜치는 `feature/KAN-{번호}_{설명}`, `fix/KAN-{번호}_{설명}` 형식을 사용했습니다.
- 커밋 메시지는 `KAN-{번호} {type}: {message}` 규칙을 따랐습니다.
- Pull Request와 코드 리뷰를 통해 `develop` 브랜치에 통합했습니다.
- Jenkins가 저장소별 CI를 실행하고 성공한 이미지를 Docker Hub에 게시한 뒤 배포 Job을 호출하도록 구성했습니다.


> MiriZoom의 계산 및 AI 답변은 참고용이며, 실제 증여와 세금 신고 전에는 세무 전문가 또는 국세청의 공식 상담을 확인하시기 바랍니다.
