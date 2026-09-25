# OTT Service 실행 가이드
사용자 이벤트 수집의 Batch API, Kafka 토픽, 재시도 및 검증 방법은 [ANALYTICS.md](ANALYTICS.md)를 참고한다.

이 방식에서는 필요한 모든 소프트웨어와 서버를 실행해 브라우저에서 바로 접속 가능하게 한다

## Content 등록부터 사용자 노출까지

1. Admin Web의 **Content → 생성**에서 작품을 만들고 상세 화면에서 제목(Localization)을 등록한다.
2. **Video 업로드 / 관리**에서 영상을 업로드한다. 인코딩 상태가 `READY`가 되어야 사용자에게 노출된다.
3. 영화는 Content 상세의 **Media**에서 편집본(예: `ORIGINAL`)을 만들고 **업로드한 Video**를 선택하여 연결한다. 시리즈는 Season/Episode를 만든 뒤 Episode 상세에서 연결한다.
4. Content와 MediaVersion을 `PUBLISHED`로 저장한다. 시리즈는 Season/Episode도 `PUBLISHED`로 설정한다.
5. **Availability**에서 서비스 국가와 시작·종료 시각(UTC)을 입력하고 `AVAILABLE`로 저장한다. 시작 시각은 포함하고 종료 시각은 제외한다. 종료를 비우면 무기한이다.
6. User Web 홈에서 현재 서비스 중인 작품을 확인한다. 연결되지 않은 업로드 영상은 노출되지 않는다. 시리즈는 첫 공개 에피소드로 연결된다.

로컬 서비스 국가는 `.env`의 `CATALOG_COUNTRY`(기본 `KR`)로 설정한다. 사용자 국가 판별은 아직 구현하지 않았으므로 조회와 재생 모두 동일한 서버 설정을 사용한다. 국가와 Metadata Locale은 별개이며 현재 작품 제목은 등록된 Locale 순서의 첫 제목을 사용한다.

`GET /api/public/contents`는 작품 단위 커서 페이지를 반환한다. 사용자 홈은 30초마다, 그리고 다시 포커스될 때 목록을 갱신한다. API는 매 요청마다 기간을 검사하므로 별도 예약 작업 없이 서비스 기간이 적용된다. 종료 후 새 재생 세션 및 HLS playlist 요청도 거부한다. 이미 내려받은 영상이나 발급된 미디어 서명 URL은 회수되지 않으며 기존 URL 만료 정책이 적용된다.

## 사전 준비
- Docker Desktop
- example 참고해 env 파일 준비

```powershell
cd ~/ott_service
copy .env.example .env
```

- 운영에서 사용하는 계정 정보 env 파일에 기입, 절대 github에 push 하지 않는다
```
MYSQL_USER=ott
MYSQL_PASSWORD=change-me
MYSQL_ROOT_PASSWORD=change-me-root
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=change-me-minio
MINIO_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3001
STORAGE_PUBLIC_ENDPOINT=http://localhost:9000

ADMIN_USERNAME=admin
ADMIN_PASSWORD=change-me-admin

LOCAL_TEST_USER_ENABLED=false
LOCAL_TEST_USER_PASSWORD=
USER_ACCESS_TOKEN_SECRET=replace-with-at-least-32-random-characters
REDIS_HOST=localhost
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
WATCH_EVENTS_TOPIC=watch-events
WATCH_EVENTS_DLQ_TOPIC=watch-events.dlq
WATCH_EVENTS_PARTITIONS=12
PLAYBACK_CONSUMER_GROUP=playback-progress-group

```

테스트 사용자 계정이 필요하면 env에 아래처럼 설정한다.

```text
LOCAL_TEST_USER_ENABLED=true
LOCAL_TEST_USER_PASSWORD=원하는_로컬_비밀번호
```

테스트 사용자 ID는 `ott_test_user`다. 비밀번호 값은 절대 커밋하지 않는다.

Compose와 Spring `application.yml`은 같은 환경 변수 이름을 사용한다.
DB 계정은 `MYSQL_USER` / `MYSQL_PASSWORD`, 로컬 MinIO 계정은
`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`로 설정한다.
IDE에서 Spring을 실행할 때는 루트 `.env`를 실행 구성의 환경 변수로 불러와야 한다.
Spring은 `.env` 파일을 자동으로 읽지 않는다.
`DB_URL`, `STORAGE_ENDPOINT`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`는
로컬 실행 시 localhost를 기본으로 사용하며 Compose에서는 컨테이너 내부 주소를 전달한다.

## 실행
Backend Dockerfile은 미리 빌드한 JAR을 복사한다. Java 코드나 `application.yml`을 변경했다면
Compose 실행 전에 JAR을 다시 빌드해야 한다. `docker compose up --build`만으로는 JAR이 갱신되지 않는다.

```powershell
.\backend\gradlew.bat -p backend :admin-api:bootJar :user-api:bootJar :media-worker:bootJar :playback-worker:bootJar :event-archive:archiveDistribution :event-analytics:analyticsDistribution
if ($LASTEXITCODE -ne 0) { throw "Backend JAR build failed" }
docker compose --env-file .env up -d --build
```

## 데이터 삭제
```powershell
docker compose down -v
```

## 주의 사항
- API와 Worker는 MySQL 연결 검사 및 필요한 초기화 컨테이너가 성공한 뒤 시작한다.
- 기존 MySQL 볼륨이 있으면 `.env`의 비밀번호 변경만으로 DB 계정 비밀번호가 바뀌지 않는다.

## 접속 주소

- admin-web: `http://localhost:3001`
- user-web: `http://localhost:3000`
- admin-api: `http://localhost:8080`
- user-api: `http://localhost:8081`
- MinIO Console: `http://localhost:9001`


## 계정 사용

admin-web은 admin-api의 HTTP Basic 계정을 사용한다.

- ID: `ADMIN_USERNAME`
- PW: `ADMIN_PASSWORD`

user-web은 user-api의 사용자 계정을 사용한다.

로컬 테스트 계정을 켠 경우:

- ID: `ott_test_user`
- PW: `LOCAL_TEST_USER_PASSWORD`


---


# 수정용 서버 ide 실행 방법
이 방식에서는 다음만 Docker로 띄운다.

- MySQL
- MinIO
- MinIO bucket init
- Redis
- Kafka
- media-worker
- playback-worker

Spring API와 Next.js는 로컬에서 실행한다.

## 준비

```powershell
copy .env.example .env
```

```powershell
cd ~\ott_service\backend
.\gradlew.bat clean build :event-archive:archiveDistribution :event-analytics:analyticsDistribution
```

```powershell
cd ~\ott_service\backend
docker compose --env-file ../.env up -d --build
```


## Next.js 로컬 실행

```powershell
cd ~\ott_service\frontend
pnpm install
```

admin-web:

```powershell
$env:NEXT_PUBLIC_ADMIN_API_BASE_URL="http://localhost:8080"
pnpm dev:admin
```

주소:

```text
http://localhost:3001
```

user-web:

```powershell
$env:NEXT_PUBLIC_USER_API_BASE_URL="http://localhost:8081"
pnpm dev:user
```

주소:

```text
http://localhost:3000
```
