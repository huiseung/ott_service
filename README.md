# OTT Service 실행 가이드
이 방식에서는 필요한 모든 소프트웨어와 서버를 실행해 브라우저에서 바로 접속 가능하게 한다

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
DB_USERNAME=ott
DB_PASSWORD=change-me
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=change-me-minio

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

## 실행
```powershell
docker compose --env-file .env up -d --build
```

## 데이터 삭제
```powershell
docker compose down -v
```

## 주의 사항
- user-api, admin-api, media-worker 의 실행 시점이 간혹 너무 빨라 다운 되는 경우 재시작 필요

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
.\gradlew.bat clean build
```

```powershell
cd ~\ott_service\backend
docker compose --env-file .env up -d --build
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
