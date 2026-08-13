# 관리자 접근 로그 SSE Nginx 운영 가이드

## 1. 문서 목적

이 문서는 관리자 페이지에서 사용자 접근 로그를 실시간으로 표시하기 위해 사용하는 SSE(Server-Sent Events)를 Nginx 뒤에서 안정적으로 운영하는 방법을 설명한다.

현재 SSE API는 다음 경로를 사용한다.

```text
GET /api/admin/access-logs/stream
```

현재 백엔드는 다음과 같이 동작한다.

- Spring MVC의 `SseEmitter`를 사용한다.
- 서버가 약 20초마다 하트비트를 전송한다.
- 프론트엔드는 `Authorization` 헤더에 JWT Access Token을 전달한다.
- 재연결 시 `Last-Event-ID` 헤더를 전달한다.
- 백엔드는 최근 접근 이벤트 500개를 인스턴스 메모리에 보관한다.
- 관리자 한 명당 SSE 연결을 최대 3개까지 유지한다.

## 2. 권장 Nginx 설정

다음은 백엔드가 `127.0.0.1:8080`에서 실행된다는 가정의 예시다. 실제 운영 환경의 서버 주소와 도메인에 맞게 수정해야 한다.

```nginx
upstream mirizoom_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name your-domain.example.com;

    # SSE 전용 경로는 일반 API보다 구체적인 location으로 분리한다.
    location = /api/admin/access-logs/stream {
        proxy_pass http://mirizoom_backend;

        # upstream과 HTTP/1.1로 통신한다.
        proxy_http_version 1.1;

        # 클라이언트가 보낸 hop-by-hop Connection 헤더는 upstream에 전달하지 않는다.
        proxy_set_header Connection "";

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 관리자 인증과 SSE 재연결 정보를 전달한다.
        proxy_set_header Authorization $http_authorization;
        proxy_set_header Last-Event-ID $http_last_event_id;

        # 이벤트를 모아서 보내지 않고 수신 즉시 프론트엔드로 전달한다.
        proxy_buffering off;
        proxy_cache off;

        # 작은 SSE 이벤트가 압축 계층에 머무르는 것을 방지한다.
        gzip off;

        # 하트비트 20초보다 충분히 긴 시간을 지정한다.
        proxy_read_timeout 75s;
        send_timeout 75s;

        # 스트리밍이 시작된 후 다른 upstream으로 재시도하지 않는다.
        proxy_next_upstream off;

        # 백엔드의 401, 403 등의 응답을 Nginx 오류 페이지로 변경하지 않는다.
        proxy_intercept_errors off;
    }

    # 일반 API 프록시 설정
    location /api/ {
        proxy_pass http://mirizoom_backend;

        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Authorization $http_authorization;
    }
}
```

TLS를 Nginx에서 종료한다면 `listen 443 ssl;` 및 인증서 설정을 적용하고, SSE 경로에도 동일한 프록시 설정을 사용한다.

## 3. 핵심 설정 설명

### 3.1 `proxy_buffering off`

Nginx의 프록시 응답 버퍼링 기본값은 `on`이다. 버퍼링이 활성화되어 있으면 Spring이 이벤트를 전송해도 Nginx가 데이터를 일정량 모은 후 브라우저로 전달할 수 있다.

이 경우 관리자 화면에서는 다음 현상이 발생할 수 있다.

- 로그가 실시간으로 한 건씩 표시되지 않는다.
- 여러 로그가 한꺼번에 표시된다.
- 하트비트가 브라우저에 늦게 도착한다.
- 중간 네트워크 장비가 연결을 유휴 상태로 판단할 수 있다.

따라서 SSE 전용 경로에는 다음 설정이 필요하다.

```nginx
proxy_buffering off;
```

백엔드 컨트롤러도 다음 응답 헤더를 전달한다.

```http
X-Accel-Buffering: no
```

Nginx는 이 헤더를 통해서도 버퍼링을 비활성화할 수 있다. 다만 SSE 전용 `location`에도 명시적으로 설정하는 것이 운영 설정을 파악하기 쉽다.

상위 Nginx 설정에 다음 내용이 있으면 백엔드의 `X-Accel-Buffering` 헤더가 무시될 수 있으므로 주의한다.

```nginx
proxy_ignore_headers X-Accel-Buffering;
```

### 3.2 `proxy_read_timeout`

`proxy_read_timeout`은 SSE 연결의 전체 유지 시간을 제한하는 값이 아니다. Nginx가 upstream으로부터 다음 데이터를 받을 때까지 허용하는 최대 유휴 시간이다.

현재 백엔드는 20초마다 하트비트를 전송하므로 다음 설정을 사용할 수 있다.

```nginx
proxy_read_timeout 75s;
```

네트워크 지연을 더 넉넉하게 허용하려면 다음과 같이 설정할 수 있다.

```nginx
proxy_read_timeout 120s;
send_timeout 120s;
```

시간 제한을 지나치게 길게 설정하면 이미 끊어진 upstream 연결이 늦게 정리될 수 있다. 일반적으로 하트비트 간격의 3배에서 6배 정도로 설정한다.

### 3.3 `proxy_http_version 1.1`

SSE는 응답 크기와 종료 시점을 미리 알 수 없는 장기 스트리밍 응답이다. 구버전 Nginx의 upstream 기본 통신 버전 차이를 피하기 위해 HTTP/1.1을 명시한다.

```nginx
proxy_http_version 1.1;
proxy_set_header Connection "";
```

SSE는 WebSocket이 아니므로 다음 WebSocket 업그레이드 설정을 추가하지 않는다.

```nginx
# SSE에는 사용하지 않는다.
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

### 3.4 인증 및 재연결 헤더

현재 프론트엔드는 기본 `EventSource`가 아니라 `fetch()`를 사용하여 SSE를 수신한다. 따라서 다음 요청 헤더를 전달할 수 있다.

```http
Authorization: Bearer {ACCESS_TOKEN}
Last-Event-ID: {LAST_RECEIVED_EVENT_ID}
```

Nginx는 일반적으로 요청 헤더를 upstream으로 전달하지만, 상위 설정의 영향을 줄이고 운영 의도를 명확하게 하기 위해 다음과 같이 선언한다.

```nginx
proxy_set_header Authorization $http_authorization;
proxy_set_header Last-Event-ID $http_last_event_id;
```

`Authorization` 헤더가 전달되지 않으면 인증 실패로 `401`이 발생할 수 있다. 인증은 되었지만 ROOT 권한이 아니면 현재 SSE 컨트롤러 검증에 의해 `403`이 발생한다.

### 3.5 압축 및 캐시 비활성화

SSE 전용 경로에서는 다음 설정을 권장한다.

```nginx
gzip off;
proxy_cache off;
```

압축 계층이 작은 이벤트를 일정량 모으면 이벤트 도착이 지연될 수 있다. 캐시는 장시간 유지되는 사용자별 인증 스트림에 적합하지 않다.

### 3.6 청크 전송 설정

일부 SSE 예제에서는 다음 설정을 사용한다.

```nginx
chunked_transfer_encoding off;
```

그러나 SSE는 응답 길이를 미리 결정할 수 없으므로 HTTP/1.1의 청크 전송을 정상적으로 사용할 수 있다. 구형 프록시와의 호환성 문제가 실제로 확인되지 않았다면 `chunked_transfer_encoding`의 기본값을 유지한다.

## 4. 다중 인스턴스 환경

현재 SSE 구독자 목록과 최근 이벤트 500개는 각 Spring 인스턴스의 메모리에 저장된다. 따라서 여러 백엔드 인스턴스를 운영할 때는 다음 상황을 고려해야 한다.

```text
최초 SSE 연결  → 인스턴스 A
로그 발생      → 인스턴스 B
SSE 재연결     → 인스턴스 C
```

### 4.1 실시간 이벤트 공유

인스턴스 B에서 발생한 로그를 인스턴스 A에 연결된 관리자에게 보내려면 인스턴스 간 이벤트 전달 채널이 필요하다.

실시간 전달만 필요하면 Redis Pub/Sub을 사용할 수 있다.

```text
인스턴스 B에서 로그 발생
        ↓
Redis 채널에 Publish
        ↓
모든 백엔드 인스턴스가 Subscribe
        ↓
각 인스턴스가 자신에게 연결된 SseEmitter로 전송
```

Redis 구독 스레드에서 `SseEmitter.send()`를 직접 수행하지 않는다. 구독 스레드는 이벤트를 받은 후 SSE 전송 실행기에 작업을 등록해야 한다. 느린 브라우저 연결이 Redis 메시지 소비를 막는 것을 방지하기 위해서다.

### 4.2 재연결 이벤트 복구

Redis Pub/Sub 메시지는 저장되지 않는다. 따라서 다음 상황의 누락 이벤트를 복구할 수 없다.

- 인스턴스가 재시작된 경우
- Redis 구독 연결이 잠시 끊긴 경우
- 배포 중 특정 인스턴스가 중단된 경우
- 재연결 요청이 기존 이벤트를 보관하지 않은 다른 인스턴스로 전달된 경우

`Last-Event-ID`를 이용한 재전송까지 다중 인스턴스에서 보장하려면 다음 중 하나가 필요하다.

- Redis Streams에 최근 이벤트 저장
- MySQL에 접근 이벤트 저장 후 ID 기준 재조회
- 현재 인메모리 버퍼를 유지하면서 로드밸런서 sticky session 적용

sticky session은 재연결을 동일 인스턴스로 유도할 뿐 서버 재시작 시 복구까지 보장하지 않는다. 운영 안정성이 중요하면 Redis Streams 또는 데이터베이스 저장 방식을 사용한다.

### 4.3 감사 로그와 접근 로그의 역할 분리

관리자 감사 로그와 실시간 사용자 접근 로그는 목적이 다르다.

- 관리자 감사 로그: 관리자가 수행한 변경 작업을 MySQL에 영구 보관한다.
- 사용자 접근 로그 SSE: 관리자 화면에 사용자 요청 상태를 실시간으로 전달한다.

규제, 보안 감사 또는 장기 검색이 필요한 접근 로그라면 SSE 전송 여부와 별개로 데이터베이스 또는 로그 수집 시스템에 저장해야 한다.

## 5. 외부 인프라 시간 제한

Nginx 설정이 정상이어도 Nginx 앞에 있는 장비의 유휴 시간 제한이 하트비트 주기보다 짧으면 연결이 종료될 수 있다.

다음 구성 요소를 함께 확인한다.

- 클라우드 로드밸런서
- CDN
- API Gateway
- Kubernetes Ingress
- 방화벽
- 사내 프록시

모든 계층의 유휴 시간 제한은 현재 하트비트 간격인 20초보다 충분히 길어야 한다.

## 6. 연결 수와 운영 자원

SSE 연결은 요청이 완료될 때까지 장시간 열린 파일 디스크립터와 소켓을 사용한다. 동시 관리자 연결이 많아지면 다음 항목을 확인한다.

- Nginx `worker_connections`
- 운영체제 파일 디스크립터 제한
- upstream keepalive 설정
- Spring MVC 비동기 실행기 크기
- SSE 전송 실행기의 큐 크기
- 관리자별 최대 연결 수

현재 애플리케이션은 관리자 한 명당 최대 3개 연결을 유지하고 오래된 연결부터 종료한다. Nginx와 운영체제 제한은 전체 관리자 수와 브라우저 탭 수를 고려하여 설정한다.

## 7. 설정 검증 및 반영

Nginx 설정 문법을 먼저 검사한다.

```bash
sudo nginx -t
```

검사가 성공한 경우 중단 없이 설정을 다시 불러온다.

```bash
sudo systemctl reload nginx
```

설정 오류가 있는 상태에서 바로 재시작하지 않는다. 먼저 `nginx -t` 결과를 확인한다.

## 8. SSE 연결 점검

`curl`의 출력 버퍼링을 비활성화하여 연결을 확인한다.

```bash
curl -N \
  -H "Accept: text/event-stream" \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  https://your-domain.example.com/api/admin/access-logs/stream
```

정상 연결에서는 다음 내용을 확인한다.

1. 연결 직후 `connected` 이벤트가 전달된다.
2. 약 20초마다 하트비트가 전달된다.
3. 새로운 사용자 API 요청이 발생하면 `access-log` 이벤트가 전달된다.
4. 연결을 끊고 `Last-Event-ID`를 지정하여 재연결하면 누락 이벤트가 재전송된다.

재연결 테스트 예시는 다음과 같다.

```bash
curl -N \
  -H "Accept: text/event-stream" \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -H "Last-Event-ID: PREVIOUS_EVENT_ID" \
  https://your-domain.example.com/api/admin/access-logs/stream
```

실제 Access Token을 쉘 기록, 문서, Nginx 접근 로그 또는 화면 캡처에 남기지 않도록 주의한다.

## 9. 장애 점검표

### 이벤트가 한꺼번에 표시되는 경우

- `proxy_buffering off`가 SSE 전용 `location`에 적용됐는지 확인한다.
- 상위 설정의 `proxy_ignore_headers X-Accel-Buffering` 여부를 확인한다.
- CDN 또는 다른 리버스 프록시가 응답을 버퍼링하는지 확인한다.
- 전역 gzip 설정이 SSE 경로에서 비활성화됐는지 확인한다.

### 약 1분마다 연결이 끊기는 경우

- `proxy_read_timeout` 값을 확인한다.
- 백엔드 하트비트가 실제로 20초마다 전송되는지 확인한다.
- 로드밸런서 또는 CDN의 유휴 시간 제한을 확인한다.
- Nginx 오류 로그에서 upstream timeout 여부를 확인한다.

### `401 Unauthorized`가 발생하는 경우

- 브라우저 요청에 `Authorization: Bearer ...` 헤더가 있는지 확인한다.
- Nginx가 `$http_authorization`을 upstream에 전달하는지 확인한다.
- Access Token 만료 및 폐기 상태를 확인한다.

### `403 Forbidden`이 발생하는 경우

- 로그인 계정의 데이터베이스 기준 역할이 `ROOT`인지 확인한다.
- `AdminAuthorizationFilter` 로그의 사용자 ID와 역할을 확인한다.
- JWT의 역할만 확인하지 말고 데이터베이스의 현재 역할을 확인한다.

### 재연결 후 일부 로그가 누락되는 경우

- 프론트엔드가 마지막으로 수신한 이벤트 ID를 보관하는지 확인한다.
- 재연결 요청에 `Last-Event-ID`가 포함됐는지 확인한다.
- Nginx가 `Last-Event-ID`를 upstream으로 전달하는지 확인한다.
- 마지막 이벤트가 인메모리 500개 범위를 벗어났는지 확인한다.
- 재연결이 다른 백엔드 인스턴스로 전달됐는지 확인한다.

## 10. 공식 참고 문서

- [Nginx 프록시 모듈](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [Nginx `proxy_buffering`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering)
- [Nginx `proxy_read_timeout`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout)
- [Nginx `proxy_http_version`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_http_version)
- [Nginx `chunked_transfer_encoding`](https://nginx.org/en/docs/http/ngx_http_core_module.html#chunked_transfer_encoding)

