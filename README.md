# 🧥 옷장을 부탁해 (Otboo)

> 날씨·취향 기반 의상 조합 추천 + OOTD 피드 소셜 서비스  
> 팀 프로젝트 포크 후 개인적으로 아키텍처 개선 및 기능 고도화 진행 중

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-green)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis%20Stream→Kafka-리팩토링중-red)](https://kafka.apache.org/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-적용중-yellow)](https://www.elastic.co/)
[![Datadog](https://img.shields.io/badge/Datadog-모니터링중-purple)](https://www.datadoghq.com/)

🔗 [배포 링크](http://otboo-alb-1790211223.ap-northeast-2.elb.amazonaws.com/#/closet) &nbsp;|&nbsp;
🔍 [SonarQube](https://sonarcloud.io/project/overview?id=codeit-team2-advanced-project_sb06-otboo-team2) &nbsp;|&nbsp;
🗒️ [트러블슈팅 Notion](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)

> 원본 프로젝트: [codeit-team2-advanced-project/sb06-otboo-team2](https://github.com/codeit-team2-advanced-project/sb06-otboo-team2) (5인 팀 프로젝트, 2026.01.22 ~ 02.27)  
> 본 저장소는 개인 아키텍처 개선 및 기술 고도화를 목적으로 포크한 버전입니다.

---

## 📌 목차

1. [담당 기능 요약](#-담당-기능-요약)
2. [기술적 의사결정](#-기술적-의사결정)
3. [트러블슈팅](#-트러블슈팅)
4. [개인 개선 작업 (포크 후)](#-개인-개선-작업-포크-후)
5. [성능 측정 JMeter](#-성능-측정-jmeter)
6. [모니터링 Datadog](#-모니터링-datadog)
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
| 대용량 알림 통계 | Spring Batch + ShedLock | 카테시안 곱 해결, 분산 중복 실행 방지 |
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

### 6. Spring Batch 카테시안 곱 문제

**상황**: 알림 통계 배치에서 User ↔ Feed ↔ Like ↔ Comment 1:N 관계를 `LEFT JOIN`으로 한 번에 조회  
→ 유저 1명 기준: 피드 20개 × 좋아요 30개 × 댓글 40개 = **24,000행** 생성, 심각한 I/O 병목

**해결**:
- 멀티 조인 제거 → **스칼라 서브쿼리**로 피드 수, 좋아요 수, 댓글 수를 각각 독립 쿼리로 조회
- 엔티티 대신 **DTO 프로젝션** 사용 → `LazyInitializationException` 방지, N+1 문제 제거

**결과**: DB 응답을 유저당 1행으로 압축, 배치 처리 시간 단축

---

### 7. 다중 서버 환경에서 배치 중복 실행

**상황**: 서버 2대 이상에서 동일한 배치 스케줄러가 동시에 실행되어 통계 데이터 중복 처리

**해결**: `ShedLock` 도입 → DB 기반 분산 락으로 한 서버만 배치 실행 보장

**향후 계획**: 배치를 앱 서버에서 분리하여 Jenkins에서 배치 전용 서버를 실행, 서비스 서버 부담 제거

---

## 🚀 개인 개선 작업 (포크 후)

### 1. Redis Stream → Kafka 리팩토링 (진행 중)

팀 프로젝트에서 Redis Stream을 선택한 이유는 당시 서비스 규모에서 운영 비용과 실시간성의 균형이 맞았기 때문입니다. 하지만 직접 운영해보면서 다음 한계를 확인했습니다.

**한계**:
- PEL 재전송 스케줄러를 **개발자가 직접 구현**해야 하는 관리 비용
- 도메인이 많아질수록 스트림 관리 복잡도 증가
- Redis 단일 장애 지점(SPOF) 위험

**Kafka 전환 후 기대 효과**:

| 항목 | Redis Stream | Kafka |
|------|-------------|-------|
| 내구성 | 인메모리 (AOF 설정 필요) | 디스크 기반, 기본 보장 |
| PEL 관리 | 직접 구현 필요 | 자동 처리 |
| 파티셔닝 | 제한적 | 유연한 수평 확장 |
| 운영 복잡도 | 낮음 | 높음 (규모 확장 시 유리) |

> ⚙️ **진행 중** — Consumer Group 및 메시지 직렬화 설계 중

---

### 2. Elasticsearch 적용 (진행 중)

**적용 대상**: 의상 검색, 피드 검색

**도입 이유**:
- 기존 LIKE 쿼리 기반 검색 → 인덱스 미사용, 풀스캔 발생
- 의상 태그·속성 다중 조건 검색 시 QueryDSL만으로 한계
- 한국어 형태소 분석 기반 검색 품질 향상 (nori 토크나이저)

> ⚙️ **진행 중** — 인덱스 매핑 설계 및 동기화 전략(이벤트 기반 vs 배치) 검토 중

---

## 📊 성능 측정 (JMeter)

> 🔄 **측정 진행 중** — Before/After 수치 측정 예정

| 측정 항목 | Before | After | 개선율 |
|-----------|--------|-------|--------|
| DM 전송 TPS | - | - | - |
| 알림 SSE 동시 연결 수 | - | - | - |
| 의상 검색 응답시간 (p95) | - | - | - |
| 배치 처리 소요시간 | - | - | - |

**테스트 시나리오**
- 동시 사용자 N명 DM 전송 시 처리량 및 응답시간
- SSE 다중 연결 시 서버 메모리 사용량
- Elasticsearch 전환 전후 검색 응답시간 비교 (LIKE 쿼리 vs ES)
- Kafka 전환 전후 메시지 처리 지연시간 비교

---

## 📈 모니터링 (Datadog)

> 🔄 **연동 진행 중**

**수집 지표**
- JVM Heap 사용량, GC 빈도
- Redis / Kafka Consumer Lag
- API 응답시간 p50 / p95 / p99
- ECS Task CPU·메모리 사용률

**알림 설정 예정**
- Consumer Lag 임계치 초과 시 Slack 알림
- Circuit Breaker 상태 변경 시 Slack/이메일 알림 (기존 로그만 남기던 부분 고도화)
- p99 응답시간 500ms 초과 시 알림

> 📸 대시보드 스크린샷 추가 예정

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
| Framework | Spring Boot 3.x, Spring Security, Spring WebFlux, Spring Batch |
| Database | PostgreSQL, Redis |
| Message Broker | Redis Stream → **Kafka** (전환 중) |
| Search | **Elasticsearch + nori** (적용 중) |
| Data Access | Spring Data JPA, QueryDSL |
| Cloud | AWS ECS (Fargate), ECR, RDS, ElastiCache, S3, ALB |
| Monitoring | **Datadog** (연동 중) |
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

### 모니터링 실행 (선택)

```bash
docker-compose -f docker-compose-monitoring.yml up -d
```

---

## 👤 Author

**이호건** &nbsp;|&nbsp; [GitHub](https://github.com/HOGUN00)
