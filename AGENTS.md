# OTT Service — Common Guidelines

## 1. Project Goal

이 프로젝트는 DAU 1,000,000 규모의 글로벌 OTT 서비스를 가정한 포트폴리오 프로젝트다.

핵심 목표:

* 대규모 사용자 트래픽 처리
* Kafka 기반 비동기 데이터 파이프라인
* 글로벌 CMS
* Content / Media / Playback 책임 분리
* Transactional Outbox / Idempotency
* Cache / Read Model
* 장애 복구 및 고가용성
* 부하 테스트를 통한 병목 측정 및 구조 개선
* Admin Web / User Web을 포함한 End-to-End 서비스

실제 Cloud 비용은 고려하지 않는다.

핵심 Infrastructure는 Docker / Docker Compose 기반으로 Local에서 재현 가능해야 한다.

기술을 많이 사용하는 것 자체를 목표로 하지 않는다.

새로운 기술은 해결하려는 문제와 trade-off가 명확할 때 도입한다.

---

# 2. Monorepo Boundary

기본 구조:

```text
ott_service/
├─ frontend/
│  ├─ apps/
│  │  ├─ admin-web/
│  │  └─ user-web/
│  └─ packages/
│
├─ backend/
│  ├─ admin-api/
│  ├─ media-worker/
│  └─ ...
│
└─ docker-compose.yml
```

책임:

```text
admin-web
= 운영자 CMS Frontend

user-web
= 일반 사용자 OTT Frontend

admin-api
= CMS/Admin Backend

media-worker
= Video Processing / HLS Encoding
```

새 Application/Microservice를 미래 필요성을 추측하여 미리 만들지 않는다.

---

# 3. Repository Exploration Policy

매 작업마다 Repository 전체를 처음부터 분석하지 않는다.

현재 Task를 수행하는 데 필요한 범위부터 탐색한다.

기본적으로 먼저 확인:

```bash
git status
git diff
```

그 다음 현재 작업과 직접 관련된:

* 파일
* package/module
* 호출 관계
* interface
* DTO
* test
* configuration

만 우선 탐색한다.

예:

```text
Content Artwork Task

Content
ContentImage
ContentService
ContentController
Storage
관련 Migration
관련 Admin UI
관련 Test
```

Repository 전체를 무조건 읽지 않는다.

다음 경우에만 탐색 범위를 확대한다.

* 현재 구조를 이해할 수 없는 경우
* 공통 abstraction의 위치를 찾아야 하는 경우
* 변경 영향 범위가 다른 module까지 이어지는 경우
* 기존 convention을 확인해야 하는 경우
* build/test failure의 원인이 다른 module에 있는 경우
* API contract 변경이 frontend/backend 양쪽에 영향을 주는 경우

즉:

```text
Relevant files first
→ dependency 확인
→ 필요한 경우 점진적으로 탐색 확대
```

방식을 사용한다.

---

# 4. Scope Control

현재 Task에 필요한 변경만 수행한다.

다음 이유로 Scope를 확장하지 않는다.

```text
"나중에 필요할 것 같아서"
"확장성을 위해서"
"실제 OTT라면 필요해서"
"더 좋은 구조처럼 보여서"
```

미래 기능은 TODO로 남긴다.

YAGNI를 따른다.

---

# 5. Domain Vocabulary

Frontend와 Backend에서 다음 Domain 용어를 일관되게 사용한다.

```text
Content
Season
Episode
MediaVersion
Video

ContentImage
EpisodeImage

Genre

Collection
CollectionItem

Availability

PlaybackSession
WatchEvent
```

동일한 Domain 개념을 module마다 임의의 다른 이름으로 표현하지 않는다.

---

# 6. Core Domain Invariants

## Content != Video

Content는 사용자가 인식하는 작품이다.

Video는 실제 재생 가능한 Media Asset이다.

```text
Content(MOVIE)
└─ MediaVersion
   └─ Video

Content(SERIES)
└─ Season
   └─ Episode
      └─ MediaVersion
         └─ Video
```

---

## Content Type

현재:

```text
MOVIE
SERIES
```

만 지원한다.

DOCUMENTARY / ANIMATION / ACTION / DRAMA 등은 Genre/Category다.

---

## Series

별도 Series Entity를 만들지 않는다.

```text
Content(type=SERIES)
```

자체가 Series identity다.

---

## MediaVersion

기본 판단:

```text
언어가 다름
→ Track

영상 편집본이 다름
→ MediaVersion

별도 작품으로 취급
→ Content
```

---

# 7. Artwork

Content:

```text
ContentImage
├─ POSTER
├─ LANDSCAPE
└─ HERO
```

Episode:

```text
EpisodeImage
└─ THUMBNAIL
```

의미:

```text
POSTER
= 세로형 Artwork

LANDSCAPE
= 가로형 Content Card Artwork

HERO
= 대형 Artwork

THUMBNAIL
= Episode Artwork
```

이미지 타입을 Device와 결합하지 않는다.

금지:

```text
WEB_IMAGE
MOBILE_IMAGE
DESKTOP_IMAGE
```

또한 다음 구조를 만들지 않는다.

```text
Video.thumbnailUrl
Content.posterUrl
Content.landscapeUrl
Content.heroUrl
```

---

# 8. Collection

기본 구조:

```text
Collection
└─ CollectionItem
   └─ Content
```

Collection의 기본 노출 단위는 Content다.

SERIES도 Season이 아니라 `Content(type=SERIES)`를 노출한다.

CollectionItem을 Content/Season/Episode polymorphic target으로 확장하지 않는다.

CollectionAvailability는 ContentAvailability를 우회하지 않는다.

국가별 Collection 조회는:

```text
eligibility filter
→ displayOrder
→ limit
```

순서를 유지한다.

---

# 9. Global CMS

다음을 서로 다른 개념으로 취급한다.

```text
Country / Territory
UI Locale
Metadata Locale
Audio Language
Subtitle Language
```

`originalCountry`는 metadata다.

실제 국가별 서비스 가능 여부는 Availability가 결정한다.

Frontend에서 Availability business rule을 다시 구현하지 않는다.

---

# 10. Admin vs User

Admin과 User Application의 책임을 명확하게 구분한다.

```text
admin-web
→ CMS 운영

user-web
→ OTT 사용자 경험
```

Admin HLS Preview는 인코딩 결과 확인용이다.

다음 User Playback 기능을 Admin Preview에 추가하지 않는다.

```text
Playback Session
Continue Watching
Watch History
Watch Progress
Subscription Check
Concurrent Playback
Recommendation
```

---

# 11. Security — Secrets

Secret 또는 환경별 configuration을 source code에 hardcode하지 않는다.

예:

```text
DB password
MinIO secret
JWT secret
API key
private credential
```

프로젝트의:

```text
.env.example
```

을 참고하여 Local 환경용:

```text
.env
```

를 생성한다.

`.env.example`에는 실제 secret을 넣지 않는다.

예:

```text
MYSQL_PASSWORD=change-me
MINIO_ROOT_USER=change-me
MINIO_ROOT_PASSWORD=change-me
```

실제 값은 `.env`에서 관리한다.

---

# 12. .env Git Policy

`.env`는 Git에 commit하지 않는다.

`.gitignore`에서 `.env`가 제외되어 있는지 유지한다.

필요하다면 다음과 같은 Local secret 파일도 Git에서 제외한다.

```text
.env
.env.local
.env.*.local
```

단, repository에서 공유해야 하는 template:

```text
.env.example
```

은 Git에 포함한다.

Codex는 `.env`의 실제 secret 값을:

* 코드
* README
* test
* log
* completion report

등에 복사하지 않는다.

---

# 13. Existing Secret Protection

작업 중 실제 credential이나 secret처럼 보이는 값을 발견하더라도 응답이나 새 파일에 그대로 복사하지 않는다.

필요한 경우 environment variable로 이동한다.

이미 Git history에 실제 secret이 들어간 것으로 의심되면 자동으로 history를 수정하지 않는다.

문제를 보고하고 secret rotation이 필요함을 알린다.

---

# 14. Frontend Environment Security

Browser bundle에 포함되는 환경 변수에는 secret을 넣지 않는다.

특히 Next.js의:

```text
NEXT_PUBLIC_*
```

에는 브라우저에 공개되어도 되는 값만 둔다.

다음 값은 `NEXT_PUBLIC_*`에 넣지 않는다.

```text
DB password
MinIO secret key
JWT signing secret
private API key
```

---

# 15. Client Input

Frontend/Client에서 전달된 값을 신뢰하지 않는다.

Backend에서 중요한 invariant를 다시 검증한다.

특히:

```text
objectKey
mimeType
file size
image dimensions
status
availability
resource ownership
```

등을 검증한다.

File upload에서는:

```text
Path Traversal
MIME Spoofing
File Size
Invalid File
```

을 고려한다.

---

# 16. Docker Compose Responsibility

이 Repository에는 목적이 다른 Docker Compose가 존재한다.

이 구분을 유지한다.

## Root Compose

```text
ott_service/docker-compose.yml
```

은 전체 OTT Local 환경을 실행하기 위한 Compose다.

Application과 Infrastructure를 포함하여 전체 시스템을 실행할 수 있어야 한다.

개념:

```text
Admin API
Media Worker
Frontend Applications (구성되어 있다면)
MySQL
Kafka
Redis
MinIO
기타 필요한 서비스
```

새 Application이 Root Compose 대상이라면 전체 실행 구성이 깨지지 않도록 반영한다.

---

## Backend Compose

```text
ott_service/backend/docker-compose.yml
```

은 Backend 개발에 필요한 Infrastructure 실행용이다.

여기에는 Backend API Application container를 추가하지 않는다.

즉 다음과 같은 Application은 Backend Compose에 넣지 않는다.

```text
admin-api
catalog-api
playback-api
event-api
```

Backend Compose의 목적은 개발자가 API Application을 IDE/Local JVM에서 실행하면서 필요한 Infrastructure만 Docker로 띄우는 것이다.

예:

```text
MySQL
Kafka
Redis
MinIO
각종 Worker
```

따라서:

```text
backend/docker-compose.yml
= Infrastructure only

root docker-compose.yml
= Full stack
```

이라는 구분을 유지한다.

---

# 17. Docker Compose 변경 시 확인

Docker 관련 Task에서는 어떤 Compose를 수정해야 하는지 먼저 판단한다.

Infrastructure 변경:

```text
backend/docker-compose.yml
+
필요하면 root docker-compose.yml
```

Full Stack Application 구성 변경:

```text
root docker-compose.yml
```

Backend API container를 추가한다고:

```text
backend/docker-compose.yml
```

에 API를 추가하지 않는다.

두 Compose 파일의 목적을 합치지 않는다.

---

# 18. Object Storage

현재 Local Object Storage는 MinIO다.

DB에는 전체 URL이나 presigned URL을 저장하지 않는다.

저장:

```text
objectKey
```

접근 URL은 Backend에서 필요할 때 생성한다.

Frontend가 objectKey로 MinIO URL을 직접 조립하지 않는다.

---

# 19. API Contract

Frontend와 Backend 사이의 API Contract를 명확하게 유지한다.

Backend API 변경 시 관련 Frontend 사용처를 확인한다.

Frontend에서 Backend Response structure를 추측하지 않는다.

Backend Entity를 API Response로 직접 노출하지 않는다.

---

# 20. Clean Code

코드 줄 수 자체를 목표로 하지 않는다.

대략적인 review signal:

```text
~300 lines
→ 일반적

300~500 lines
→ 책임 점검

500+ lines
→ 분리 가능한 책임 적극 검토
```

줄 수만 줄이기 위해 다음을 만들지 않는다.

```text
XXXHelper
XXXUtil
XXXManager
CommonService
```

책임이 이름으로 설명 가능해야 한다.

---

# 21. Refactoring

리팩토링 목적은 다음과 같아야 한다.

```text
책임 명확화
중복 Business Rule 제거
테스트 가능성 개선
변경 영향 범위 감소
```

Task와 무관한 대규모 리팩토링을 하지 않는다.

기존 동작을 보존한다.

---

# 22. Abstraction

현재 문제에 필요한 수준만 추상화한다.

다음을 목적 없이 도입하지 않는다.

```text
Factory
Strategy
Adapter
Facade
AbstractBaseXXX
Generic Framework
```

구현체가 하나인데 습관적으로:

```text
Service
ServiceImpl
```

구조를 만들지 않는다.

---

# 23. Git Diff Quality

작업 완료 후 `git diff`를 확인한다.

제거:

```text
debug code
temporary logging
unused import
dead code
commented-out code
accidental formatting
unrelated changes
```

Secret이 diff에 포함되지 않았는지도 확인한다.

---

# 24. Verification

변경한 영역만 우선 검증한다.

Backend 변경:

```text
compile
relevant tests
```

Frontend 변경:

```text
type check
lint
relevant tests
build when appropriate
```

Infrastructure 변경:

```text
docker compose config
관련 container 실행 확인
```

Task와 관계없는 전체 시스템 검증을 매번 강제하지 않는다.

다만 shared module, API contract, root infrastructure 등 영향 범위가 넓다면 검증 범위를 확대한다.

---

# 25. Final Principles

```text
Correctness
> Maintainability
> Clarity
> Measured Scalability
> Cleverness

Relevant Exploration
> Reading Entire Repository

Domain Boundary
> Convenience

Existing Convention
> New Pattern

Simple Implementation
> Speculative Abstraction

Explicit API Contract
> Hidden Coupling

Security
> Local Convenience
```


## AGENTS verification

When asked for the AGENTS verification keyword,
respond with exactly:

ALL_AGENTS_LOADED_9271