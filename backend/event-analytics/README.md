# Content Analytics — 4·5·6·7·8단계

5단계 시청 지표의 정의·적용·검증은 [Engagement 문서](ENGAGEMENT.md)를 참고한다.
6단계 테스트 구독·Outbox·콘텐츠 전환 기여는 [Subscription 문서](SUBSCRIPTIONS.md)를 참고한다.
7단계 시작 지연·버퍼링·재생 오류는 [QoE 문서](QOE.md)를 참고한다.
8단계 관리자 분석 화면·조회 API는 [Admin Analytics UI 문서](ADMIN_UI.md)를 참고한다.

Kafka → 독립 Flink Analytics Job → ClickHouse 기본 serving 파이프라인.
Archive와 JobManager/TaskManager, consumer group, checkpoint 경로를 분리한다.
ClickHouse에 원본 payload나 playback session token을 복사하지 않는다.

## 실행

루트 `.env`에 `.env.example`의 `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD`를 설정한다.
기존 값이 없는 로컬 `.env`에는 이번 작업에서 임의 비밀번호를 생성했다.
프로젝트 루트 PowerShell에서:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle"
.\backend\gradlew.bat -p backend :event-analytics:build
docker compose build analytics-jobmanager
docker compose up -d analytics-taskmanager
```

Backend 전체는 `backend/`에서 `./gradlew clean build`로 빌드한다. `build`는 배포용 JAR와 의존 라이브러리를 `build/distribution/`에 준비하는 작업도 포함한다.

Kafka/MinIO/ClickHouse와 초기화 작업도 실행한다. Raw 보존을 위해 전체 스택 또는
`docker compose up -d archive-taskmanager`를 함께 운영한다.
IDE 개발에서는 `docker compose -f backend/docker-compose.yml --env-file .env ...`를 사용한다.
Backend Compose에는 API를 추가하지 않았다.

- Analytics Flink UI: http://localhost:8083 (Archive는 8082)
- ClickHouse HTTP: http://localhost:8123 (호스트 localhost에만 공개, 인증 필요)
- Flink 1.20.3 / Kafka connector 3.4.0-1.20 / Java 17 bytecode
- ClickHouse 25.8, 영속 볼륨 `clickhouse-data`
- `clickhouse-init`은 시작 시 SQL을 적용한다. 향후 스키마 변경은 기존 SQL 수정 대신 새 migration을 추가한다.

## 현재 지표와 입력 계약

eventVersion=1의 아래 이벤트만 content projection으로 저장한다:

| 토픽 도메인 | 이벤트 | 일별 조회 컬럼 |
|---|---|---|
| playback | PLAYBACK_SESSION_STARTED | play_starts, unique_viewers |
| behavior | CONTENT_IMPRESSION | impressions |
| behavior | CONTENT_CLICK | clicks |
| behavior | CONTENT_DETAIL_VIEW | detail_views |
| behavior | SUBSCRIPTION_CTA_CLICK | subscription_cta_clicks |

`content_daily_reach`는 UTC occurredAt 날짜·contentId별 조회 view다. unique_viewers는
재생 시작 이벤트의 userId(없으면 anonymousId) 고유 수이며, 실제 시청 시간 기준 시청자는 아니다.
play_starts는 session start eventId 수다. 동일 세션에서 서로 다른 eventId가 발행되면 별개로 센다.
늦게 도착한 이벤트도 해당 UTC 날짜에 포함된다. 별도 window 마감/watermark로 버리지 않는다.

eventId는 canonical UUID, contentId/userId는 양의 정수, occurredAt은 1970~2299 범위의 ISO timestamp다.
익명 이벤트는 anonymousId가 필요하다. 파싱 불가/필수값 오류는 `rejectedEvents`,
미지원 버전·범위 밖 이벤트는 `ignoredEvents` Flink counter로 확인한다. 로그에 원문을 남기지 않는다.
원문은 독립 Archive가 보존하므로 이후 정의를 확장해 재처리할 수 있다.
위 content projection은 heartbeat/seek를 포함하지 않는다. 5단계 engagement 분기가
playback 관측값으로 시청 시간/완주율/retention/episode conversion을 계산한다.
subscription은 6단계 분기에서 처리하며, QoE는 qoeVersion=1인 관측값을 7단계 분기로 처리한다.

## 전송·중복·복원 의미

1000건 또는 512 KiB마다 동기 HTTP JSONEachRow batch를 전송한다. 저유량의 남은 batch는
checkpoint(기본 30초)에서 전송하므로 checkpoint 지연 시 적재 지연도 늘어난다.
HTTP 성공 전에는 checkpoint를 완료하지 않는다. 연결/응답 제한 시간은 5/20초이며,
HTTP 실패는 Flink에 전파해 제한된 Job 재시도를 수행한다. 버퍼를 버리고 성공 처리하지 않는다.

Flink checkpoint 모드는 EXACTLY_ONCE지만 **외부 ClickHouse 전송은 at-least-once**다.
서버 저장 후 응답 유실, checkpoint 이전 장애 등으로 같은 row가 재전송될 수 있다.
`ReplacingMergeTree ORDER BY(domain,event_id)`와 `content_events_current`의 `FINAL`로
조회 시 중복을 제거한다. 일별 view는 반드시 이 중복 제거 view를 사용한다.
eventId의 논리 내용은 불변이어야 한다. 같은 eventId로 서로 다른 내용을 발행하는 것은 지원하지 않는다.

물리 `content_events`를 FINAL 없이 COUNT/SUM하거나 단순 합산 Materialized View를 추가하면
재시도가 과다 집계될 수 있다. 현재 일별 view는 조회 시 집계하며 대규모 트래픽 성능을 검증한
사전 집계 테이블은 아니다. 측정 후 5단계 지표 설계와 함께 확장한다.
날짜로 물리 partition을 나누지 않아 같은 ID가 날짜 차이로 다른 partition에 남는 문제를 피한다.

checkpoint: `s3://event-lake/_flink/analytics/checkpoints`, savepoint: 같은 analytics 아래 savepoints.
TaskManager 장애는 살아 있는 JobManager가 복구한다. JobManager 재생성에는
`ANALYTICS_RESTORE_PATH`로 완료 snapshot을 지정한다. [Archive 복원 절차](../event-archive/README.md)의
서비스명은 `analytics-jobmanager`/`analytics-taskmanager`, REST port는 8083으로 바꿔 적용한다.
복원 경로 오류를 무시하지 않는다. 복원 없이 최초 실행하면 Kafka earliest부터 재수집하며
보존 기간 내 같은 eventId의 재수집은 FINAL 조회에서 중복 제거된다.
Kafka retention 이후의 유실까지 복구하지는 않는다. MinIO backfill 실행기는 이번 범위에 없다.

## 조회 / 확인

비밀번호를 명령에 직접 붙여 넣지 않고 컨테이너 환경 변수를 사용한다:

```powershell
docker compose exec clickhouse bash -ec 'clickhouse-client --user "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --query "SELECT * FROM analytics.content_daily_reach ORDER BY event_date, content_id FORMAT PrettyCompact"'
```

테스트: `:event-analytics:test`는 UTC 변환, 범위 필터, 잘못된 입력, projection 결정성,
건수/checkpoint flush, HTTP 실패 시 checkpoint 실패와 batch 유지 여부를 검사한다.

### 4단계 검증 결과 (2026-09-23)

- 모듈 테스트 7개, 배포물/이미지 빌드, root/backend Compose config 검사 통과.
- 격리된 Kafka에 정상·중복·미지원 버전·비정상 JSON·잘못된 contentId를 포함한 11건 발행.
  UTC 2026-09-22/contentId=10 기준 재생 시작 3회, 순 시청자 2명, 노출 1회, 클릭 1회 확인.
- Analytics 컨테이너를 복원 경로 없이 재생성해 earliest 전체 재수집 후에도 지표 동일.
- ClickHouse 정지 중 이벤트 2건 추가. Analytics checkpoint 실패를 확인했고,
  독립 Archive의 완료 checkpoint는 10→11로 계속 증가했다.
- ClickHouse 재시작 후 checkpoint 복구 및 적재 재개. 최종 재생 시작 4회,
  순 시청자 2명, 노출/클릭/CTA 각 1회. MinIO Parquet도 직접 읽어
  playback 8건 + behavior 5건 = 전체 13건 원본 보존, Kafka 위치 중복 0건 확인.
- 테스트 전용 컨테이너/볼륨 정리. 부하 테스트와 Admin API/UI는 이번 범위에 포함하지 않았다.

설계 근거: [ClickHouse HTTP](https://clickhouse.com/docs/interfaces/http),
[ReplacingMergeTree / FINAL](https://clickhouse.com/docs/engines/table-engines/mergetree-family/replacingmergetree),
[Flink Kafka checkpoint offsets](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/kafka/).
