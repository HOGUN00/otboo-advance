# 🧥 옷장을 부탁해 (Otboo)

> 날씨·취향 기반 의상 조합 추천 + OOTD 피드 소셜 서비스 \
> 팀 프로젝트 종료 후 성능 검증을 통해 오류를 발견·수정하고, 병목을 분석해 구조적 개선 방향을 도출한 개인 고도화 포크

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-green)](https://spring.io/projects/spring-boot)

🎬 [팀 프로젝트 시연 영상](https://drive.google.com/file/d/15Aw6SN9HEt85HFxmfV5XhMdY0WsPqMJM/view)  | 
🔍 [팀 프로젝트 SonarQube Cloud](https://sonarcloud.io/project/overview?id=codeit-team2-advanced-project_sb06-otboo-team2)  | 
🗒️ [개발 리포트](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)

> 원본 프로젝트: [codeit-team2-advanced-project/sb06-otboo-team2](https://github.com/codeit-team2-advanced-project/sb06-otboo-team2) \
> 팀 프로젝트: 5인, 2026.01.22 ~ 02.27 \
> 개인 고도화: 2026.07 ~ 현재 \
> 팀 프로젝트에서 실시간 DM·알림 시스템을 담당하여 WebSocket, SSE, Redis Streams, Spring Batch 등을 구현했습니다. \
> 프로젝트 종료 후 개인 포크에서 기존 구현을 대상으로 부하 테스트와 성능 측정을 진행하고, 발견한 오류를 수정하며 병목 원인과 개선 방향을 분석했습니다.

---

## 🏗️ 시스템 아키텍처

> <img width="1800" height="1125" alt="otboo-architecture" src="https://github.com/user-attachments/assets/5994d221-7de7-4a61-88fc-431e95684506" />


---

## 🙋 담당 기능 요약

팀 프로젝트에서 **실시간 DM 및 알림 시스템**을 담당했습니다.

| 기능         | 핵심 기술                        | 설명                       |
| ---------- | ---------------------------- | ------------------------ |
| 실시간 1:1 DM | WebSocket + Redis Streams    | 다중 서버 환경 세션 불일치 해결       |
| 실시간 알림     | SSE + Redis Streams          | 단방향 알림, 재연결 시 미수신 이벤트 보정 |
| 대용량 알림 통계  | Spring Batch + ShedLock      | 통계 쿼리 설계, 분산 중복 실행 방지    |
| 장애 대응      | Resilience4j Circuit Breaker | Redis 장애 전파 차단           |

---

## 🔍 핵심 구현과 문제 해결

> 문제의 배경과 기술 선택 근거, 검증 과정은 각 항목의 개발리포트 링크에 정리했습니다.

### 팀 프로젝트 구현

- [WebSocket·SSE 선택](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bd203c86c59804aa7fdc24fc9e63cdf): DM은 양방향 WebSocket, 알림은 단방향 SSE로 분리 → 기능별 통신 방향에 맞는 실시간 채널 구성
- [다중 서버 실시간 메시징](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c59806d9054cad610599a14): 서버별 로컬 연결로 다른 서버의 사용자에게 DM·알림을 전달할 수 없음 → Redis Streams로 메시지를 공유하고 해당 사용자가 연결된 서버에서 최종 전송
- [Redis Streams 선택](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f4ae68c13faaa6d41d): Consumer의 처리 확인과 실패 메시지 재처리가 가능한 브로커 비교 → 기존 Redis와 ACK·PEL을 활용할 수 있는 Redis Streams 선택 → 추적·재처리 범위와 운영 한계 확인
- [실패 메시지 재처리 및 Redis 장애 대응](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f0b479f9c41a95c359): ACK되지 않은 메시지가 PEL에 잔류 → 재처리 스케줄러 구현 → Redis 반복 장애가 애플리케이션으로 전파되지 않도록 Circuit Breaker 적용
- [배치 중복 실행 방지](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c598010b3bef72b942d1eee): 다중 서버에서 동일한 스케줄러가 중복 실행될 가능성 → ShedLock 적용 → 동일한 실행 시점에는 여러 서버 중 하나만 배치를 실행하도록 제어

### 개인 고도화 및 검증

- [통계 쿼리 성능 검증](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f7825edf754e325ece): 다중 LEFT JOIN의 중간 결과 증가를 피하려고 스칼라 서브쿼리 적용 → 규모별 `EXPLAIN ANALYZE` 비교 → 데이터 규모와 PostgreSQL 설정에 따라 결과가 달라질 수 있음을 확인
- [배치 삭제 검증 공백](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f9ab74c3d2026fc93b): 성능 측정 중 Job은 완료됐지만 실제 삭제가 수행되지 않는 문제 발견 → Writer의 삭제 로직 수정 → 실행 전후 DB 상태와 처리 건수를 교차 확인해 45,552건 삭제 검증
- [DM DB 커넥션 풀 병목](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c598011a903c678635d1e9a): 부하 증가에 따라 메시지 타임아웃 급증 → 스레드 덤프에서 처리 스레드의 DB 커넥션 대기 확인 → 풀 확대의 한계와 DB 왕복 축소·처리량 제어 방향 도출
- [SSE 재연결 알림 유실](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c59805e9a44f902fbe9a7c2): 이전 emitter의 지연된 완료 콜백이 새 연결까지 삭제 → `ConcurrentHashMap.remove(key, value)` 적용 → 재연결 재현 테스트로 정상 수신 확인

---

## 📊 성능 측정

k6·PostgreSQL `EXPLAIN ANALYZE`·Spring Batch 메타테이블·서버 스레드 덤프로 실시간 파이프라인과 배치를 계층별(쿼리 → 배치 → 파이프라인 → 인프라)로 나눠 실측했습니다.

| 측정 항목 | 결과 |
|-----------|------|
| WebSocket DM 부하테스트 | 부하 증가에 따라 메시지 타임아웃이 급증했고, 스레드 덤프를 통해 HikariCP 커넥션 대기를 1차 병목으로 확인 |
| Redis Streams Consumer 직접 측정 | 앱·DB 경로를 우회한 조건에서 단일 Consumer 약 6,300 msg/sec |
| 통계 쿼리 비교 | 소규모에서는 LEFT JOIN이 유리했지만 데이터 증가 후 스칼라 서브쿼리가 유리해지는 구간을 확인했으며, PostgreSQL 설정에 따라 결과가 달라질 수 있었음 |
| Spring Batch 삭제 검증 | 알림 정리 배치 45,552건을 52초에 삭제하고 실행 전후 DB 상태와 Batch 메타데이터를 교차 검증 |
| SSE 배치 알림 팬아웃 | 500 VU 조건에서 발송 대상 325/325 수신, 비대상 오발송 0건 |

> 📐 각 수치의 측정 구간과 파이프라인 간 관계는 [성능 측정 범위 정리](https://app.notion.com/p/3c6203c86c5980728926d20a6576963a?source=copy_link)에서 확인할 수 있습니다.

---

## ✅ 코드 품질 관리

SonarQube를 GitHub Actions와 연동하여 PR 단위로 품질을 자동 검증했습니다.

* **테스트 커버리지 80% 이상** 강제(DTO, config 제외)
* 코드 스멜·보안 취약점 자동 검출
* 빌드 + 테스트 통과를 Merge 조건으로 설정
* 코드 리뷰에서는 기계적 검증을 SonarQube에 맡기고, **도메인 로직 정합성과 설계 개선 제안**에 집중했습니다.

🔗 [SonarQube 대시보드](https://sonarcloud.io/project/overview?id=codeit-team2-advanced-project_sb06-otboo-team2)

---

## 🛠 기술 스택

| 분류             | 기술                                                |
| -------------- | ------------------------------------------------- |
| Language       | Java 17                                           |
| Framework      | Spring Boot 3.5.10, Spring Security, Spring Batch    |
| Database       | PostgreSQL, Redis                                 |
| Message Broker | Redis Streams                                     |
| Data Access    | Spring Data JPA, QueryDSL                         |
| Cloud          | AWS ECS (Fargate), ECR, RDS, ElastiCache, S3, ALB |
| Load Test      | k6 (WebSocket), xk6-sse (SSE)                     |
| Resilience     | Resilience4j (Circuit Breaker), ShedLock          |
| CI/CD          | GitHub Actions                                    |
| Code Quality   | SonarQube Cloud                                   |
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
