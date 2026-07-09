# 🧥 옷장을 부탁해 (Otboo)

> 날씨·취향 기반 의상 조합 추천 + OOTD 피드 소셜 서비스  
> 팀 프로젝트 포크 후 개인적으로 아키텍처 개선 및 기능 고도화 진행 중

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-green)](https://spring.io/projects/spring-boot)

🔗 [배포 링크](http://otboo-alb-1790211223.ap-northeast-2.elb.amazonaws.com/#/closet) &nbsp;|&nbsp;
🔍 [SonarQube](https://sonarcloud.io/project/overview?id=codeit-team2-advanced-project_sb06-otboo-team2) &nbsp;|&nbsp;
🗒️ [개발 리포트 Notion](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)

> 원본 프로젝트: [codeit-team2-advanced-project/sb06-otboo-team2](https://github.com/codeit-team2-advanced-project/sb06-otboo-team2) (5인 팀 프로젝트, 2026.01.22 ~ 02.27)  
> 본 저장소는 개인 아키텍처 개선 및 기술 고도화를 목적으로 포크한 버전입니다.

---

## 📌 목차

1. [담당 기능 요약](#-담당-기능-요약)
2. [기술적 의사결정](#-기술적-의사결정)
3. [트러블슈팅](#-트러블슈팅)
4. [성능 측정](#-성능-측정)
5. [병목 분석 방법](#-병목-분석-방법)
6. [향후 개선 사항](#-향후-개선-사항)
7. [코드 품질 관리](#-코드-품질-관리)
8. [기술 스택](#-기술-스택)
9. [로컬 실행 방법](#-로컬-실행-방법)

---

## 🙋 담당 기능 요약

팀 프로젝트에서 **실시간 DM 및 알림 시스템 전체**를 담당했습니다.

| 기능 | 핵심 기술 | 설명 |
|------|-----------|------|
| 실시간 1:1 DM | WebSocket + Redis Stream | 다중 서버 환경 세션 불일치 해결 |
| 실시간 알림 | SSE + Redis Stream | 단방향 알림, 연결 안정성 보장 |
| 대용량 알림 통계 | Spring Batch + ShedLock | 카테시안 곱 검증, 분산 중복 실행 방지 |
| 장애 대응 | Resilience4j Circuit Breaker | Redis 장애 전파 차단 |

---

## 🤔 기술적 의사결정

### 왜 WebSocket과 SSE를 함께 사용했나?

DM과 알림 모두 실시간이 필요하지만, 성격이 다릅니다.

- **DM**: 사용자 간 양방향 통신 → **WebSocket**
- **알림**: 서버 → 클라이언트 단방향 전달 → **SSE**

WebSocket은 양방향 커넥션을 유지하므로 단방향으로 충분한 알림에 사용하면 불필요한 서버 리소스가 낭비됩니다. 용도에 맞게 프로토콜을 분리하여 서버 부하를 줄였습니다.

---

### 왜 Redis Stream을 선택했나?

서버 다중화 환경에서 세션 불일치 문제를 해결하기 위해 메시지 브로커가 필요했습니다.  
**"높은 실시간성"과 "메시지 유실 방지"** 두 기준으로 후보군을 비교했습니다.

| 브로커 | 메시지 유실 방지 | 응답 속도 | 운영 비용 | 비고 |
|--------|---------------|-----------|-----------|------|
| Redis Pub/Sub | ❌ Fire-and-Forget | 빠름 | 낮음 | 유실 방지 불가 |
| RabbitMQ | ✅ ACK 지원 | 보통 (디스크 기반) | 중간 | 지연 발생 |
| Kafka | ✅ | 높은 처리량에 유리 | 높음 | 현재 규모 대비 과함 |
| **Redis Stream** | ✅ ACK + PEL | **빠름 (인메모리)** | 낮음 | **✅ 선택** |

Redis Stream은 인메모리 기반으로 응답이 빠르면서도, Consumer Group·ACK·PEL(Pending Entries List)을 통해 메시지 유실을 방지할 수 있어 선택했습니다.

---

## 🔥 트러블슈팅

### 1. SSE 연결 끊김 + 재연결 시 메시지 유실

**상황**: 사용자가 일정 시간 활동하지 않으면 SSE 연결이 끊어지고, 재연결 전에 발생한 알림이 유실됨

**해결**:
- 스케줄러로 주기적인 Ping 전송 → 유휴 상태에서도 연결 유지
- 재연결 시 클라이언트가 `lastEventId`를 파라미터로 전송 → 서버가 해당 ID 이후 미수신 메시지를 조회해 재전송

```
클라이언트 재연결 요청 (lastEventId 포함)
→ 서버: Redis에서 lastEventId 이후 메시지 조회
→ 누락된 메시지 순서대로 재전송
```

---

### 2. 다중 서버 환경에서 세션 불일치

**상황**: `WebSocketSession`, `SseEmitter`가 각 서버의 로컬 메모리에만 존재  
→ A 서버에 접속한 유저가 B 서버에 접속한 유저에게 DM 전송 시, 상대방 세션을 찾을 수 없어 전달 불가

**해결**: Redis Stream을 메시지 브로커로 도입
- 모든 서버가 동일한 Redis Stream을 구독
- 어느 서버에서 메시지가 발행되어도, 수신자와 연결된 서버가 스트림에서 읽어 전달

```
User A (Server 1) → DM 전송
→ Redis Stream에 발행
→ Server 1, Server 2 모두 스트림 구독 중
→ User B가 연결된 Server 2가 수신 후 WebSocketSession으로 전달
```

**결과**: 분산 환경에서 DM·알림 누락 제거

---

### 3. Redis 직렬화 3가지 함정

Redis Stream에 메시지를 저장할 때 `GenericJackson2JsonRedisSerializer`에 ObjectMapper를 복사해 `JavaTimeModule`을 등록하는 방식을 사용했으나, 세 가지 문제가 연달아 발생했습니다.

**문제 1 - LocalDateTime 직렬화 오류**: 기본 설정에서 LocalDateTime이 배열 형태로 직렬화됨

**문제 2 - 클래스 타입 정보 포함**: 역직렬화 편의를 위해 `activateDefaultTyping`을 설정하면 직렬화 데이터에 클래스 경로가 포함됨  
→ 추후 클래스 위치 변경 시 역직렬화 오류 발생 가능

**문제 3 - 필드 변경 시 역직렬화 오류**: 오래된 캐시 데이터의 필드와 현재 클래스 필드가 다를 경우 매핑 실패

**해결**: 클래스 타입 정보에서 자유로운 **String JSON 직렬화 방식**으로 전환  
\+ Spring이 기본 제공하는 `ObjectMapper` 활용 (Spring 공식 문서 확인)
- `JavaTimeModule` 기본 포함 → LocalDateTime 직렬화 해결
- `FAIL_ON_UNKNOWN_PROPERTIES = false` 기본 설정 → 필드 불일치 시 무시

별도 ObjectMapper 빈을 등록하지 않고 Spring 제공 ObjectMapper를 그대로 사용하여 세 가지 문제를 동시에 해결했습니다.

---

### 4. Redis 장애 시 애플리케이션으로 장애 전파

**상황**: PEL 재전송 스케줄러가 주기적으로 Redis에 접근하는 구조에서, Redis 다운 시 반복 실패가 앱 전체로 전파

**해결**: `Resilience4j` Circuit Breaker 적용
- Redis 호출 실패가 임계치를 넘으면 Circuit Open → 이후 Redis 호출 차단, Fallback 반환
- Half-Open 상태에서 일부 요청으로 복구 여부 확인 후 자동 재복구
- 상태 변경 시 로그 기록 (추후 Slack/이메일 알림으로 고도화 예정)

**결과**: Redis 장애가 서비스 전체로 전파되지 않음

---

### 5. Redis 메모리 파편화

**상황**: Maxlen(키당 메시지 개수 제한) + TTL 설정으로 쓰기·삭제가 빈번하게 발생  
→ 삭제된 메모리 공간이 새 데이터보다 작아 할당 실패, 공간 낭비 발생

**해결**: `activedefrag` 설정 활성화  
→ 서비스 중단 없이 런타임 중 동적으로 파편화된 메모리를 재조합

---

### 6. Spring Batch 카테시안 곱 문제 — 설계 검증

**상황**: 알림 통계 배치에서 User ↔ Feed ↔ Like ↔ Comment 1:N 관계를 `LEFT JOIN`으로 한 번에 조회하면 유저 1명 기준 피드 20개 × 좋아요 30개 × 댓글 40개 = **24,000행**이 생길 수 있다고 판단해, 처음부터 **스칼라 서브쿼리 3개**(피드 수, 좋아요 수, 댓글 수)로 설계했습니다.

**검증**: 이 설계 판단을 실제로 검증한 적은 없었기에, `EXPLAIN ANALYZE`로 naive `LEFT JOIN` 버전과 데이터 규모별(N=100/500/1,000/5,000)로 비교 측정했습니다. 소규모(N=100)에서는 오히려 naive join이 4~6배 빨랐고, N=1,000부터 스칼라 서브쿼리가 역전했습니다. N=5,000에서는 PostgreSQL JIT 컴파일이 이 쿼리 패턴에 불리하게 작용해 일시적으로 역전됐는데, JIT를 끄고 재측정하니 스칼라 서브쿼리가 다시 우위를 보였습니다.

**결과**: 기존 설계(스칼라 서브쿼리)를 그대로 유지. 엔티티 대신 DTO 프로젝션 사용으로 `LazyInitializationException` 방지 및 N+1 문제 회피도 함께 확인.

---

### 7. 다중 서버 환경에서 배치 중복 실행

**상황**: 서버 2대 이상에서 동일한 배치 스케줄러가 동시에 실행되어 통계 데이터 중복 처리

**해결**: `ShedLock` 도입 → DB 기반 분산 락으로 한 서버만 배치 실행 보장

---

### 8. 배치 삭제 로직 누락

**상황**: Spring Batch 처리 시간을 실측하는 과정에서, 알림 정리 배치(`deleteOldNotificationsJob`)가 이름과 달리 실제로는 삭제를 수행하지 않는다는 것을 발견했습니다. Writer가 `merge()`만 호출하고 있어 실질적인 삭제 효과가 없었고, 기존 통합 테스트가 H2 프로파일이라 실제 Postgres 환경에서 커밋 동작이 검증된 적이 없었던 것이 원인이었습니다.

**해결**: `remove()`로 수정. 커서 기반 리더(`JpaCursorItemReader`)는 스냅샷 기준으로 동작해 삭제 중 페이징 스킵 위험이 없다는 것을 확인하고 그대로 유지했습니다. 소규모(149건) 검증 후 전체(45,552건)로 확장, 삭제 전후 COUNT 비교로 스킵·중복 없음을 검증했습니다.

**결과**: 45,552건을 52초(약 870 rows/sec)에 정상 처리.

---

### 9. 동시 다발 메시지 전송 시 DB 커넥션 풀 고갈로 인한 메시지 유실

**상황**: WebSocket DM 동시접속 부하테스트(k6) 중, 기본 HikariCP 풀(10)이 50건의 동시 메시지 전송 요청만으로 포화되어 다수가 유실되는 것을 발견했습니다. DM 저장 로직이 sender/receiver 조회, 채팅방 조회/생성, 메시지 저장까지 DB 왕복을 4~5회 수행하는 구조라, 소규모 동시 쓰기 요청만으로도 풀이 바로 고갈됐습니다.

**발견**: 이 저하가 latency 지표로는 전혀 드러나지 않았습니다. p95는 500 ~ 5,000명 구간에서 54~148ms로 거의 변화가 없었는데, 이는 latency 지표가 "성공한 요청"만 집계하기 때문이었습니다. 실제로는 풀 고갈로 유실되는 비율(500명 15.5% → 5,000명 95.9%)만 계속 증가했습니다 — 시스템이 느려지는 게 아니라 "일부만 성공하고 나머지는 버려지는" 방식으로 무너진다는 것을 실측으로 확인했습니다.

**해결**: 풀 크기를 50으로 확장해 포화 임계점을 500건 선으로 이동시켰으나, 이는 임계점을 늦출 뿐 근본 해결은 아니라고 판단했습니다. 구체적 개선 방향은 [향후 개선 사항](#-향후-개선-사항) 참고.

---

### 10. SSE 재연결 시 알림 유실

**상황**: SSE 알림 채널 부하테스트 중 일부 유저가 알림을 받지 못하는 현상을 발견했습니다. 처음엔 "구독 확인(ack) 전송과 registry 등록 사이의 좁은 시간창" 가설을 세웠으나, 실측 결과 이 구간이 최대 7ms로 매우 짧고 테스트의 알림 발행은 그보다 훨씬 뒤에 일어나 이 가설로는 설명되지 않았습니다.

**원인**: 재현을 반복하며 로그를 추적한 결과, 미수신 유저는 매번 짧은 간격을 두고 같은 계정으로 두 번 연결하고 있었습니다. `configEmitter()`의 `onCompletion` 콜백이 인스턴스 구분 없이 userId 키로만 registry를 삭제하는 구조라, 재연결 시 옛 연결의 완료 콜백이 비동기로 지연 실행되면서 방금 등록된 새 연결까지 지워버리는 문제였습니다. 별도 에러 로그도 남지 않아 원인 추적이 까다로웠습니다. 브라우저 탭 여러 개, 새로고침 직후 재연결 등 실서비스에서도 충분히 발생 가능한 시나리오입니다.

```java
// 수정 후
emitter.onCompletion(() -> {
    SseEmitter current = sseEmitterRepository.findById(userId);
    if (current == emitter) {
        sseEmitterRepository.deleteById(userId);
    }
});
```

**결과**: 키가 아닌 인스턴스 비교로 삭제하도록 수정. 재연결을 의도적으로 유도하는 재현 시나리오로 수정 전(유실)/후(정상 수신)를 직접 검증했습니다.

---

## 📊 성능 측정

k6·PostgreSQL EXPLAIN ANALYZE·Spring Batch 메타테이블로 실시간 파이프라인과 배치를 계층별(쿼리 → 배치 → 파이프라인 → 인프라)로 나눠 실측했습니다.

| 측정 항목 | 결과 |
|-----------|------|
| WebSocket DM 부하테스트 (500~5,000 VU) | 유실률 15.5%→95.9% 선형 증가, p95 latency는 54~148ms로 안정 → HikariCP 커넥션 풀이 병목임을 확인 |
| Redis Stream Consumer 순수 처리량 | 약 6,300 msg/sec (실제 서비스 경로 관측치의 20배 이상 여유) |
| 카테시안 곱 쿼리 벤치마크 | N=1,000부터 스칼라 서브쿼리가 naive LEFT JOIN 대비 우위 역전 확인 |
| Spring Batch 처리량 | 알림 정리 배치 45,552건/52초(약 870 rows/sec) |
| SSE 배치 알림 팬아웃 (500명) | 325/325 전원 수신, 오발송 0건, p95 약 420ms |

각 측정이 정확히 어느 구간을 재는지는 [개발 리포트 Notion](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)의 성능 측정 범위 정리 페이지에서 파이프라인 다이어그램과 함께 확인할 수 있습니다.

---

## 🔍 병목 분석 방법

부하테스트 중 발생한 병목은 k6 자체 지표(latency, 유실률)와 서버 스레드 덤프(`jcmd Thread.print`)로 분석했습니다. 3,000 VU 이후 서버가 일시 무응답에 빠진 원인을, 스레드 덤프에서 처리 슬롯 216개(Tomcat 200 + WebSocket inbound 16) 전부가 `HikariPool.getConnection()`에서 대기 중임을 확인해 특정했습니다. 데드락(BLOCKED 스레드 0개)과 GC 정지 가능성은 스레드 덤프 자체로 배제했습니다.

---

## 🔭 향후 개선 사항

**성능 측정으로 도출된 방향**
- PgBouncer 등 커넥션 풀러 도입 — HikariCP 풀 재포화 임계점(동시 쓰기 500건) 자체를 높이는 방향
- DM 저장 로직의 DB 왕복 횟수(4~5회) 축소
- 백프레셔 도입 — 처리 슬롯 216개 근접 시 신규 요청 조기 거절
- Redis Stream 발행을 DB 저장보다 선행시키는 Write-Ahead 구조 검토
- 위 개선 이후 커넥션 풀/스레드 수 재조정

**기타 아이디어 (미착수)**
- Redis Stream → Kafka 전환 검토 (PEL 수동 관리 비용, SPOF 리스크 고려)
- Elasticsearch 도입 검토 (의상/피드 검색 성능)
- Datadog 연동 (현재는 k6 + 스레드 덤프로 병목 분석 대체)
- 서킷브레이커 상태 변경 시 Slack/이메일 알림
- 배치를 앱 서버에서 분리해 Jenkins 전용 배치 서버로 실행

---

## ✅ 코드 품질 관리

SonarQube를 GitHub Actions와 연동하여 PR 단위로 품질을 자동 검증했습니다.

- **테스트 커버리지 80% 이상** 강제 (DTO, config 제외)
- 코드 스멜·보안 취약점 자동 검출
- 빌드 + 테스트 통과를 Merge 조건으로 설정
- 코드 리뷰는 기계적 검증은 SonarQube에 맡기고, **도메인 로직 정합성과 설계 개선 제안**에 집중

🔗 [SonarQube 대시보드](https://sonarcloud.io/project/overview?id=codeit-team2-advanced-project_sb06-otboo-team2)

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.x, Spring Security, Spring Batch |
| Database | PostgreSQL, Redis |
| Message Broker | Redis Stream |
| Data Access | Spring Data JPA, QueryDSL |
| Cloud | AWS ECS (Fargate), ECR, RDS, ElastiCache, S3, ALB |
| Load Test | k6 (WebSocket), xk6-sse (SSE) |
| Resilience | Resilience4j (Circuit Breaker), ShedLock |
| CI/CD | GitHub Actions |
| Code Quality | SonarQube (SonarCloud) |
| API Docs | Swagger (Springdoc) |
| Test | EasyRandom |

---

## 🚀 로컬 실행 방법

### 사전 요구사항
- Java 17
- Docker & Docker Compose

### 실행

```bash
git clone https://github.com/HOGUN00/otboo-advance.git
cd otboo-advance

# 환경변수 설정
cp .env.example .env
# .env 파일에 DB, Redis, S3, JWT 시크릿 등 입력

# 인프라 실행 (PostgreSQL, Redis)
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

---

## 👤 Author

**이호건** &nbsp;|&nbsp; [GitHub](https://github.com/HOGUN00)
