# 🧥 옷장을 부탁해 (Otboo)

> 날씨·취향 기반 의상 조합 추천 + OOTD 피드 소셜 서비스 \
> 팀 프로젝트 종료 후 성능 검증을 통해 오류와 병목을 발견·수정하고, 구조적 개선 방향을 도출한 개인 고도화 포크

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-green)](https://spring.io/projects/spring-boot)

🎬 [팀 프로젝트 시연 영상](https://drive.google.com/file/d/15Aw6SN9HEt85HFxmfV5XhMdY0WsPqMJM/view)  | 
🔍 [팀 프로젝트 SonarQube Cloud](https://sonarcloud.io/project/overview?id=codeit-team2-advanced-project_sb06-otboo-team2)  | 
🗒️ [개발 리포트](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)

> 원본 프로젝트: [codeit-team2-advanced-project/sb06-otboo-team2](https://github.com/codeit-team2-advanced-project/sb06-otboo-team2) \
> 팀 프로젝트: 5인, 2026.01.22 ~ 02.27 \
> 개인 고도화: 2026.07 ~ 현재 \
> 팀 프로젝트에서 실시간 DM·알림 시스템을 담당하여 WebSocket, SSE, Redis Streams, Spring Batch 등을 구현했습니다. \
> 프로젝트 종료 후 개인 포크에서 기존 구현을 대상으로 부하 테스트와 성능 측정을 진행하고, 발견한 오류와 병목을 수정·분석했습니다.

---

## 📌 목차

1. [담당 기능 요약](#-담당-기능-요약)
2. [기술적 의사결정](#-기술적-의사결정)
3. [트러블슈팅](#-트러블슈팅)
4. [성능 측정](#-성능-측정)
5. [코드 품질 관리](#-코드-품질-관리)
6. [기술 스택](#-기술-스택)
7. [로컬 실행 방법](#-로컬-실행-방법)

---

## 🙋 담당 기능 요약

팀 프로젝트에서 **실시간 DM 및 알림 시스템 전체**를 담당했습니다.

| 기능         | 핵심 기술                        | 설명                       |
| ---------- | ---------------------------- | ------------------------ |
| 실시간 1:1 DM | WebSocket + Redis Streams    | 다중 서버 환경 세션 불일치 해결       |
| 실시간 알림     | SSE + Redis Streams          | 단방향 알림, 재연결 시 미수신 이벤트 보정 |
| 대용량 알림 통계  | Spring Batch + ShedLock      | 통계 쿼리 설계, 분산 중복 실행 방지    |
| 장애 대응      | Resilience4j Circuit Breaker | Redis 장애 전파 차단           |

---

## 🤔 기술적 의사결정

### 왜 WebSocket과 SSE를 함께 사용했나?

DM과 알림 모두 실시간이 필요하지만, 성격이 다릅니다.

* **DM**: 사용자 간 양방향 통신 → **WebSocket**
* **알림**: 서버 → 클라이언트 단방향 전달 → **SSE**

DM은 송수신이 모두 필요한 반면 알림은 서버에서 클라이언트로 전달하면 충분합니다. 두 기능의 통신 방향에 맞춰 프로토콜을 분리하고, 알림은 HTTP 기반 재연결과 `lastEventId`를 활용할 수 있는 SSE로 구성했습니다.

---

### 왜 Redis Streams를 선택했나?

서버 다중화 환경에서 로컬 세션 간 메시지를 전달하고, Consumer 처리 실패 시 재시도할 수 있는 메시지 브로커가 필요했습니다. 현재 서비스 규모와 기존 Redis 인프라 활용 가능성까지 고려해 후보군을 비교했습니다.

| 브로커               | 처리 완료 확인·실패 재처리 | 강점                                 | 고려 사항                      |
| ----------------- | --------------- | ---------------------------------- | -------------------------- |
| Redis Pub/Sub     | 미지원             | 구조가 단순하고 지연이 낮음                    | 구독자가 없거나 처리에 실패하면 복구하기 어려움 |
| RabbitMQ          | ACK·재전달 지원      | 큐와 라우팅 기능이 풍부함                     | 별도 브로커 운영 필요               |
| Kafka             | 오프셋 기반 재처리      | 대규모 처리량과 이벤트 보관에 강함                | 현재 규모에서는 운영 복잡도가 큼         |
| **Redis Streams** | **ACK·PEL 지원**  | **기존 Redis 활용, Consumer Group 지원** | **PEL 복구 정책을 직접 관리해야 함**   |

Redis Streams는 기존 Redis 인프라를 활용하면서 Consumer Group·ACK·PEL(Pending Entries List)을 제공해 선택했습니다. 이를 통해 **Stream에 발행된 이후 Consumer 처리에 실패한 메시지**를 PEL에서 조회하고 재처리할 수 있도록 구성했습니다.

> 여기서 ACK는 최종 사용자의 메시지 수신 확인이 아니라, Consumer의 처리 완료 확인을 의미합니다. Stream 추가 이전의 실패나 Redis 자체 장애는 이 재처리 범위에 포함되지 않습니다.

PEL 재처리 스케줄러에는 Redis 장애 시 반복 호출이 애플리케이션으로 전파되지 않도록 Resilience4j Circuit Breaker를 적용했습니다.

---

## 🔥 트러블슈팅

> 아래는 대표 트러블슈팅 4건입니다. 세부 원인 분석과 추가 사례는 [개발 리포트](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)에서 확인할 수 있습니다.

### 1. [팀 프로젝트] [다중 서버 환경에서 세션 불일치](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c59806d9054cad610599a14)

**상황**: `WebSocketSession`, `SseEmitter`가 각 서버의 로컬 메모리에만 존재
→ A 서버에 접속한 유저가 B 서버에 접속한 유저에게 DM 전송 시, 상대방 세션을 찾을 수 없어 전달 불가

**해결**: Redis Streams를 메시지 브로커로 도입

* 서버별 Consumer Group(`group-dm-<serverId>`)이 동일한 Stream을 구독
* 각 서버가 메시지를 읽은 뒤, 수신자 세션을 보유한 서버가 WebSocket으로 전달

```
User A (Server 1) → DM 전송
→ Redis Streams에 발행
→ 서버별 Consumer Group이 메시지 소비
→ User B가 연결된 Server 2가 수신 후 WebSocketSession으로 전달
```

**결과**: 세션을 각 서버의 로컬 메모리에 유지하면서도 서버 간 DM·알림 전달이 가능한 경로를 구성했습니다.

---

### 2. [개인 고도화] [동시 다발 메시지 전송 시 DB 커넥션 풀 고갈](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c598011a903c678635d1e9a)

**상황**: WebSocket DM 부하테스트(k6) 중, 기본 HikariCP 풀(10)이 50 VU 단계에서 포화되어 다수의 메시지가 응답 제한 시간 안에 처리되지 못하는 것을 발견했습니다. DM 저장 로직이 sender/receiver 조회, 채팅방 조회/생성, 메시지 저장까지 DB 왕복을 4~5회 수행하는 구조라, 소규모 동시 쓰기 요청만으로도 풀이 바로 고갈됐습니다.

**발견**: 이 저하가 latency 지표만으로는 드러나지 않았습니다. 성공한 메시지의 왕복 지연 p95는 500~5,000 VU 구간에서 54 ~148ms였지만, 메시지 타임아웃 비율은 15.5%에서 95.9%까지 증가했습니다. 부하 증가가 성공 요청의 지연보다 **실패율 증가**로 나타난다는 것을 확인했습니다.

**분석**: 3,000 VU 이후 서버가 일시 무응답에 빠졌을 때 처리 스레드 216개(Tomcat 200 + WebSocket inbound 16)가 모두 `HikariPool.getConnection()`에서 대기 중이었습니다. 이를 통해 DB 커넥션 대기로 처리 슬롯이 점유된 상태가 핵심 병목임을 확인했습니다.

**대응 및 판단**: 풀 크기를 50으로 확장한 뒤 50 VU 재검증에서는 타임아웃이 해소됐지만, 500 VU 단계에서 다시 포화됐습니다. 풀 크기 조정은 임계점을 늦출 뿐 근본 해결은 아니라고 판단했으며, DM 저장 로직의 DB 왕복 횟수 축소와 Redis Streams 기반 전송·비동기 영속화 분리, 백프레셔 도입을 후속 개선 방향으로 도출했습니다.

구체적인 개선 방향은 [개발 리포트의 향후 개선 사항](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980ca8a29dd399e801b2d)에 정리했습니다.

---

### 3. [개인 고도화] [SSE 재연결 시 알림 유실](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c59805e9a44f902fbe9a7c2)

**상황**: SSE 알림 채널 부하테스트 중 일부 유저가 알림을 받지 못하는 현상을 발견했습니다. 처음엔 "구독 확인(ack) 전송과 registry 등록 사이의 좁은 시간창" 가설을 세웠으나, 실측 결과 이 구간이 최대 7ms로 매우 짧고 테스트의 알림 발행은 그보다 훨씬 뒤에 일어나 이 가설로는 설명되지 않았습니다.

**원인**: 재현을 반복하며 로그를 추적한 결과, 미수신 유저는 매번 짧은 간격을 두고 같은 계정으로 두 번 연결하고 있었습니다. `configEmitter()`의 `onCompletion` 콜백이 인스턴스 구분 없이 userId 키로만 registry를 삭제하는 구조라, 재연결 시 옛 연결의 완료 콜백이 비동기로 지연 실행되면서 방금 등록된 새 연결까지 지워버리는 문제였습니다.

**결과**: `ConcurrentHashMap.remove(key, value)`로 키와 인스턴스를 함께 확인해 원자적으로 삭제하도록 수정했습니다. 재연결을 의도적으로 유도하는 재현 시나리오로 수정 전 알림 유실과 수정 후 정상 수신을 직접 검증했습니다.

---

### 4. [개인 고도화] [배치 삭제 검증 공백 발견](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f9ab74c3d2026fc93b)

**상황**: Spring Batch 처리 시간을 실측하는 과정에서, 알림 정리 배치(`deleteOldNotificationsJob`)가 이름과 달리 실제로는 삭제를 수행하지 않는다는 것을 발견했습니다. Writer가 `merge()`만 호출하고 있어 실질적인 삭제 효과가 없었습니다.

**원인**: 단위 테스트는 `ItemWriter` 로직 자체만 검증했고, 통합 테스트는 배치 Job의 정상 완료(COMPLETED) 여부만 확인했습니다. 배치 실행 후 실제로 데이터가 삭제됐는지를 DB에서 직접 확인하는 테스트가 없어, `merge()`가 삭제 효과를 내지 못하는 로직 오류가 계속 통과되고 있었습니다.

**해결**: `remove()`로 수정했습니다. 소규모(149건) 검증 후 전체(45,552건)로 확장하며, 삭제 전후 COUNT와 `read_count`·`write_count`를 비교해 스킵과 중복이 없음을 직접 검증했습니다.

**결과**: 45,552건을 52초(약 870 rows/sec)에 정상 처리했습니다.

---

## 📊 성능 측정

k6·PostgreSQL `EXPLAIN ANALYZE`·Spring Batch 메타테이블·서버 스레드 덤프로 실시간 파이프라인과 배치를 계층별(쿼리 → 배치 → 파이프라인 → 인프라)로 나눠 실측했습니다.

| 측정 항목                             | 결과                                                                           |
| --------------------------------- | ---------------------------------------------------------------------------- |
| WebSocket DM 부하테스트 (500~5,000 VU) | 메시지 타임아웃 비율 15.5%→95.9%, 성공 메시지 p95 54~148ms → HikariCP 커넥션 풀이 1차 병목임을 확인    |
| Redis Streams Consumer 순수 처리량     | 약 6,300 msg/sec (서비스 경로에서 관측한 순간 최대 312 msg/sec 대비 약 20배)                    |
| 카테시안 곱 쿼리 벤치마크                    | 소규모에서는 naive LEFT JOIN이 우세했으나 N=1,000부터 역전, N=5,000에서는 JIT 설정에 따라 결과가 다시 달라짐 |
| Spring Batch 처리량                  | 알림 정리 배치 45,552건/52초(약 870 rows/sec)                                         |
| SSE 배치 알림 팬아웃 (500 VU)            | 발송 대상 325/325 전원 수신, 비대상 오발송 0건, p95 약 420ms                                 |

> 📐 각 수치의 측정 구간과 파이프라인 간 관계는 [성능 측정 범위 정리](https://app.notion.com/p/398d0c65baa980aca0bdec20cfb56c8e)에서 확인할 수 있습니다.

---

## ✅ 코드 품질 관리

SonarQube를 GitHub Actions와 연동하여 PR 단위로 품질을 자동 검증했습니다.

* **테스트 커버리지 80% 이상** 강제(DTO, config 제외)
* 코드 스멜·보안 취약점 자동 검출
* 빌드 + 테스트 통과를 Merge 조건으로 설정
* 코드 리뷰에서는 기계적 검증을 SonarQube에 맡기고, **도메인 로직 정합성과 설계 개선 제안**에 집중

🔗 [SonarQube 대시보드](https://sonarcloud.io/project/overview?id=codeit-team2-advanced-project_sb06-otboo-team2)

---

## 🛠 기술 스택

| 분류             | 기술                                                |
| -------------- | ------------------------------------------------- |
| Language       | Java 17                                           |
| Framework      | Spring Boot 3.x, Spring Security, Spring Batch    |
| Database       | PostgreSQL, Redis                                 |
| Message Broker | Redis Streams                                     |
| Data Access    | Spring Data JPA, QueryDSL                         |
| Cloud          | AWS ECS (Fargate), ECR, RDS, ElastiCache, S3, ALB |
| Load Test      | k6 (WebSocket), xk6-sse (SSE)                     |
| Resilience     | Resilience4j (Circuit Breaker), ShedLock          |
| CI/CD          | GitHub Actions                                    |
| Code Quality   | SonarQube Cloud                        |
| API Docs       | Swagger (Springdoc)                               |
| Test           | EasyRandom                                        |

---

## 🚀 로컬 실행 방법

### 사전 요구사항

* Java 17
* Docker & Docker Compose

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

**이호건**  |  [GitHub](https://github.com/HOGUN00)
