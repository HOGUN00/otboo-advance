# 🧥 옷장을 부탁해 (Otboo)

> 날씨·취향 기반 의상 조합 추천 + OOTD 피드 소셜 서비스 \
> 팀 프로젝트 종료 후 성능 검증을 통해 오류를 발견·수정하고, 병목을 분석해 구조적 개선 방향을 도출한 개인 고도화 포크

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-green)](https://spring.io/projects/spring-boot)

🎬 [팀 프로젝트 시연 영상](https://drive.google.com/file/d/15Aw6SN9HEt85HFxmfV5XhMdY0WsPqMJM/view)  | 
🗒️ [개발 리포트](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)  |
🔍 [팀 프로젝트 SonarQube Cloud · Coverage 83.3%](https://sonarcloud.io/component_measures?metric=coverage&id=codeit-team2-advanced-project_sb06-otboo-team2)  | 


> 원본 프로젝트: [codeit-team2-advanced-project/sb06-otboo-team2](https://github.com/codeit-team2-advanced-project/sb06-otboo-team2) \
> 팀 프로젝트: 5인, 2026.01.22 ~ 02.27 \
> 개인 고도화: 2026.07 ~

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
| 대용량 알림 통계  | Spring Batch + ShedLock      | 알림 통계 배치, 다중 서버 중복 실행 방지    |
| 장애 대응      | Resilience4j Circuit Breaker | Redis 장애 전파 차단           |

---

## 🔍 핵심 구현과 문제 해결

> 아래는 대표적인 구현·문제 해결 사례이며, [개발 리포트](https://www.notion.so/312203c86c5980dbafc7f1961b01eda4)에는 기술 선택 근거와 검증 과정, 그 외 구현·개선 내용을 함께 정리했습니다. 

### 팀 프로젝트 구현

- [다중 서버 실시간 메시징](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c59806d9054cad610599a14): 서버별 로컬 연결로 다른 서버의 사용자에게 DM·알림을 전달할 수 없음 → Redis Streams로 메시지를 공유하고 해당 사용자가 연결된 서버에서 최종 전송
- **Redis Streams 선택·실패 재처리 및 장애 대응**: [메시지 브로커 선택](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f4ae68c13faaa6d41d)에서 Consumer 처리 확인과 실패 재처리를 기준으로 Redis Streams 선택 → [재처리·장애 대응](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f0b479f9c41a95c359)에서 ACK·PEL 기반 재처리 스케줄러와 Circuit Breaker 적용

### 개인 고도화 및 검증

- [DM DB 커넥션 풀 병목](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c598011a903c678635d1e9a): 부하 증가에 따라 메시지 타임아웃 급증 → 스레드 덤프에서 처리 스레드의 DB 커넥션 대기 확인 → 풀 확대의 한계와 DB 왕복 축소·처리량 제어 방향 도출
- [알림 삭제 배치 구조 개선](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c5980f9ab74c3d2026fc93b): Job은 완료됐지만 실제 삭제되지 않는 문제 발견 → UUID 기반 JDBC Paging·Batch DELETE로 변경하고 재시작을 고려한 복합 정렬·날짜 기준 JobParameter 적용 → `EXPLAIN ANALYZE`로 실행 계획을 비교해 복합 인덱스 효과 검증
- [SSE 재연결 알림 유실](https://app.notion.com/p/312203c86c5980dbafc7f1961b01eda4?source=copy_link#3bb203c86c59805e9a44f902fbe9a7c2): 이전 emitter의 지연된 완료 콜백이 새 연결까지 삭제 → `ConcurrentHashMap.remove(key, value)` 적용 → 재연결 재현 테스트로 정상 수신 확인

---

## 📊 성능 측정

| 측정 항목 | 결과 |
|-----------|------|
| WebSocket DM 부하테스트 | **500 → 5,000 VU에서 메시지 타임아웃 15.5% → 95.9%** → 스레드 덤프에서 **216개 처리 스레드의 HikariCP 커넥션 대기** 확인 |
| Redis Streams Consumer 구간 측정 | 앱·DB 경로를 제외하고 Redis Streams에 직접 메시지를 주입한 조건에서 단일 Consumer **약 6,300 msg/sec** 처리 |
| 알림 삭제 Batch Paging 조회 | 47,300건 기준 첫 페이지 조회 **8.056ms → 0.066ms** → `(created_at, id)` 복합 인덱스 적용 후 **Index Only Scan** 전환 |

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
