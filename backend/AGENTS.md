# Backend Guidelines

이 파일은 `backend/` 이하 작업에 적용하는 Backend-specific guideline이다.

Root `AGENTS.md`의 공통 규칙을 함께 따른다.

---

# 1. Backend Responsibility

Backend는 다음 영역을 담당한다.

```text
CMS
Media Processing
Catalog
Playback
Event Processing
Persistence
Business Rules
```

Frontend에 Business Rule을 떠넘기지 않는다.

---

# 2. Spring / Java Convention

현재 프로젝트에서 사용하는 Java/Spring Boot convention을 우선한다.

새 library를 추가하기 전에 기존 dependency로 해결 가능한지 확인한다.

Dependency 추가 시:

```text
왜 필요한가?
기존 dependency로 해결할 수 없는가?
유지보수 비용은 적절한가?
```

를 검토한다.

---

# 3. Layer Responsibility

기본적으로:

```text
Controller
↓
Application / Service
↓
Domain
↓
Repository / Infrastructure
```

책임을 유지한다.

Controller:

```text
Request parsing
Validation trigger
Service call
Response
```

Service/Application:

```text
Use Case
Transaction orchestration
Business workflow
```

Repository:

```text
Persistence
Query
```

Infrastructure:

```text
MinIO
Kafka
외부 시스템
```

---

# 4. Controller

Controller에 다음을 넣지 않는다.

```text
Business Rule
Repository 직접 orchestration
MinIO SDK 호출
Kafka 직접 처리
복잡한 Entity mutation
```

Controller를 얇게 유지한다.

---

# 5. Entity / DTO

JPA Entity를 API Response로 직접 반환하지 않는다.

```text
Request DTO
↓
Application
↓
Entity

Entity
↓
Response DTO
```

경계를 유지한다.

API 요구사항 때문에 Entity 구조를 억지로 변경하지 않는다.

---

# 6. JPA

항상 다음을 검토한다.

```text
N+1
EAGER
CascadeType.ALL
불필요한 양방향 관계
대량 Collection loading
```

N+1을 해결하기 위해 모든 관계를 EAGER로 만들지 않는다.

필요한 Query/Projection/Fetch 전략을 사용한다.

---

# 7. Repository

Service에서 데이터를 전부 읽어 Java에서 filtering하지 않는다.

DB에서 효율적으로 처리해야 하는:

```text
Filtering
Pagination
Existence
Aggregation
JOIN
Ordering
```

은 적절한 Query를 사용한다.

새 Query를 추가하면 관련 index를 검토한다.

---

# 8. Database Integrity

중요한 invariant는 DB에서도 보장한다.

예:

```text
NOT NULL
UNIQUE
FK
```

다음 패턴만으로 uniqueness를 보장하지 않는다.

```text
exists()
→ insert()
```

Concurrency를 고려한다.

---

# 9. Flyway

이미 적용된 migration 파일을 수정하지 않는다.

Schema 변경 시 새로운 migration을 추가한다.

Entity와 Migration이 일치하는지 확인한다.

---

# 10. Transaction Boundary

`@Transactional`을 모든 public method에 습관적으로 붙이지 않는다.

Transaction은 실제 DB 작업에 필요한 최소 범위로 유지한다.

```text id="n5ac2g"
Transaction 시작
↓
DB Read / Write
↓
Transaction Commit
```

가능하면 이 범위를 짧게 유지한다.

---

## DB Transaction 내부에서 외부 Network I/O를 수행하지 않는다

원칙적으로 DB Transaction이 열린 상태에서 외부 시스템과 Network 통신하지 않는다.

대표적인 외부 작업:

```text id="s2r2ar"
HTTP API 호출
MinIO / S3 요청
Kafka Produce
SMTP
외부 인증 서버
다른 Microservice 호출
기타 Network I/O
```

다음 구조를 피한다.

```text id="u5pmmu"
@Transactional
    ↓
DB Query
    ↓
HTTP / MinIO / Kafka
    ↓
Network 응답 대기
    ↓
DB Query
    ↓
Commit
```

외부 시스템 응답을 기다리는 동안 DB Connection이 Transaction에 의해 계속 점유될 수 있다.

외부 시스템의 latency 증가나 timeout이 발생하면:

```text id="cnr03r"
External System latency 증가
        ↓
Transaction 시간 증가
        ↓
DB Connection 점유 시간 증가
        ↓
Connection Pool 부족
        ↓
새 요청의 DB Connection 대기
        ↓
API latency 증가
        ↓
Timeout 증가
        ↓
Connection Pool 고갈
```

로 장애가 전파될 수 있다.

따라서:

```text id="nyugw5"
DB Transaction
≠
External Network Transaction
```

이라는 경계를 유지한다.

---

## External I/O와 DB 작업 분리

가능하면 다음처럼 분리한다.

```text id="df2r19"
External I/O
↓
필요한 데이터 확보
↓
짧은 DB Transaction
↓
Commit
```

또는:

```text id="ev3fc6"
짧은 DB Transaction
↓
Commit
↓
External I/O
```

어느 순서가 적절한지는 consistency 요구사항에 따라 결정한다.

단순히 Transaction 밖으로 옮기는 것만으로 정합성 문제가 해결된다고 가정하지 않는다.

---

## DB + Kafka

DB 변경과 Kafka publish가 함께 성공해야 하는 Business Requirement가 있다면:

```text id="5d3x3e"
@Transactional
DB 변경
Kafka publish
Commit
```

형태의 dual write를 기본 해결책으로 사용하지 않는다.

필요하면 Transactional Outbox를 사용한다.

```text id="2i4m8k"
@Transactional
    ↓
Business Data 변경
    ↓
Outbox 저장
    ↓
Commit

Outbox Publisher
    ↓
Kafka
```

DB Transaction 안에서는 DB 작업만 빠르게 완료한다.

---

## DB + MinIO

DB와 MinIO는 하나의 Transaction이 아니다.

이미지/영상 업로드처럼 둘을 함께 변경해야 한다면 작업 순서와 partial failure를 명시적으로 설계한다.

예:

```text id="x2e1i6"
MinIO Upload
↓
짧은 DB Transaction
↓
Metadata 저장
↓
Commit
```

DB 저장이 실패하면 업로드된 Object가 orphan이 될 수 있으므로 cleanup 정책을 고려한다.

반대로 기존 Object 교체의 경우:

```text id="gczilp"
새 Object Upload
↓
DB Reference 교체
↓
Commit
↓
기존 Object Delete
```

같은 방식을 고려할 수 있다.

외부 Network I/O 전체를 하나의 긴 `@Transactional` method 안에 넣어 해결하지 않는다.

---

## Transaction 안에서 외부 호출이 불가피한 경우

정말 Business Requirement상 Transaction 중 외부 호출이 필요한 경우 무조건 금지하기보다 이유를 명확히 한다.

다음을 검토한다.

```text id="68tx3k"
왜 Transaction 내부여야 하는가?
외부 호출 latency 상한은 무엇인가?
Timeout이 설정되어 있는가?
Connection Pool에 미치는 영향은?
Retry가 Transaction 시간을 증가시키는가?
외부 장애가 DB Connection 고갈로 전파될 수 있는가?
다른 consistency 설계로 분리할 수 없는가?
```

가능하면 Transaction 밖으로 분리하는 구조를 우선한다.

---

## Transaction 내부 Retry 주의

긴 Network Retry를 DB Transaction 안에서 수행하지 않는다.

특히:

```text id="av3qks"
@Transactional
    ↓
DB Connection 획득
    ↓
External API
    ↓
timeout
    ↓
retry
    ↓
timeout
    ↓
retry
```

구조는 피한다.

Retry 시간 동안 DB Connection이 계속 점유될 수 있기 때문이다.

Retry가 필요한 외부 작업은 가능한 한 DB Transaction과 분리한다.

---

## Connection Pool 관점 Self Review

`@Transactional`이 포함된 코드를 작성하거나 수정했다면 확인한다.

```text id="xq6j8r"
[ ] Transaction 범위가 필요한 최소 수준인가?
[ ] Transaction 내부에 HTTP 호출이 있는가?
[ ] Transaction 내부에 MinIO/S3 호출이 있는가?
[ ] Transaction 내부에 Kafka Produce가 있는가?
[ ] Transaction 내부에 다른 Service Network 호출이 있는가?
[ ] Transaction 내부에 긴 Retry가 있는가?
[ ] 외부 시스템 장애 시 DB Connection을 오래 점유할 수 있는가?
[ ] Connection Pool 고갈로 장애가 전파될 가능성이 있는가?
[ ] Outbox / 비동기 처리 / Transaction 분리가 더 적절한가?
```

외부 시스템의 느린 응답이 DB Connection Pool 고갈로 전파되지 않도록 Transaction Boundary를 설계한다.

---

# 12. Kafka

대규모 이벤트 처리에는 비동기 구조를 우선 검토한다.

Consumer는 duplicate delivery 가능성을 전제로 한다.

```text
At-least-once
+
Idempotency
```

를 기본으로 한다.

Ordering 요구사항에 맞는 partition key를 선택한다.

---

# 13. Transactional Outbox

DB 변경과 Kafka publish consistency가 필요한 경우 단순 dual write를 피한다.

```text
Business Data
+
Outbox

same DB transaction
```

후 별도 Publisher가 Kafka로 전달하는 방식을 우선 검토한다.

모든 CRUD에 무조건 적용하지 않는다.

---

# 14. Retry

Retry 가능한 오류와 영구 오류를 구분한다.

무한 Retry를 만들지 않는다.

필요한 경우:

```text
retry count
exponential backoff
FAILED state
DLQ
```

를 고려한다.

Retry는 duplicate 처리를 발생시킬 수 있으므로 idempotency를 함께 검토한다.

---

# 15. MinIO / Object Storage

MinIO-specific 코드를 Domain Service 곳곳에 작성하지 않는다.

공통 low-level Storage abstraction이 존재하면 재사용한다.

예:

```text
ObjectStorage

upload
delete
createAccessUrl
```

Video와 Image가 같은 ObjectStorage를 사용한다고 Domain을 합치지 않는다.

---

# 16. Image / File Security

업로드 파일은 Backend에서 검증한다.

확인:

```text
size
MIME
실제 decode 가능 여부
dimensions
path
```

파일 확장자 또는 Frontend 전달 MIME만 신뢰하지 않는다.

Object Key는 서버에서 생성한다.

Client가 arbitrary storage path를 지정하지 못하게 한다.

---

# 17. Secrets

`.env.example`을 환경 변수 template으로 사용한다.

Local 실행에 필요한 `.env`가 없다면 `.env.example`을 참고해 생성한다.

실제 Secret을 source code에 hardcode하지 않는다.

`.env`를 Git에 추가하지 않는다.

Test fixture에도 실제 credential을 복사하지 않는다.

---

# 18. Docker Compose

`backend/docker-compose.yml`의 책임:

```text
Backend 개발용 Infrastructure only
```

여기에 Backend API Application을 추가하지 않는다.

예를 들어 다음은 넣지 않는다.

```text
admin-api
catalog-api
playback-api
event-api
```

개발자는 필요한 Infrastructure를 Docker로 실행하고 Backend Application은 IDE/JVM에서 실행할 수 있어야 한다.

Infrastructure 예:

```text
MySQL
Kafka
Redis
MinIO
```

전체 Application을 Docker로 실행하는 책임은 Repository Root의:

```text
../docker-compose.yml
```

에 있다.

이 구분을 유지한다.

---

# 19. Multiple Database

물리 DB 분리는 측정 근거가 있을 때 수행한다.

DB가 분리되어도 cross-database JPA association을 만들지 않는다.

예:

```text
ContentPopularity.contentId
```

처럼 logical identifier를 사용한다.

두 DB를 하나의 local JPA Transaction으로 처리된다고 가정하지 않는다.

XA는 특별한 이유가 없다면 사용하지 않는다.

---

# 20. Performance

최적화는 가능한 한 측정 후 수행한다.

다음을 확인한다.

```text
Query count
Query latency
N+1
DB connection
Kafka lag
Consumer throughput
```

DAU 숫자만 보고 복잡한 optimization을 미리 추가하지 않는다.

---

# 21. Backend Tests

새 Business Rule에는 관련 테스트를 추가한다.

우선:

```text
Happy Path
Boundary
Invalid State
Duplicate
Failure
Retry
Concurrency-sensitive behavior
```

를 검토한다.

private method를 테스트하기 위해 visibility를 변경하지 않는다.

Behavior를 테스트한다.

---

# 22. Backend Verification

Backend 변경 후 현재 Task에 관련된:

```text
compile
test
```

를 우선 실행한다.

Shared module 또는 광범위한 변경이면 전체 Backend test로 확대한다.

매 작은 변경마다 관계없는 모든 시스템을 실행하지 않는다.

---

# 23. Backend Self Review

완료 전 확인:

```text
[ ] Controller에 Business Logic이 있는가?
[ ] Service가 여러 책임을 가지는가?
[ ] Entity가 API에 노출되는가?
[ ] N+1이 있는가?
[ ] EAGER로 문제를 숨겼는가?
[ ] DB constraint가 필요한가?
[ ] Transaction boundary가 적절한가?
[ ] DB와 MinIO/Kafka를 하나의 Transaction처럼 가정했는가?
[ ] Race Condition이 있는가?
[ ] Retry가 duplicate를 만들 수 있는가?
[ ] Query index를 검토했는가?
[ ] Secret이 코드/diff에 포함됐는가?
[ ] backend/docker-compose.yml에 API를 추가했는가?
```


## AGENTS verification

When asked for the AGENTS verification keyword,
respond with exactly:

BACKEND_AGENTS_LOADED_9271