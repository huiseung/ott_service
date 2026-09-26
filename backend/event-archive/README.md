# Raw Event Archive — 2·3단계

독립된 Flink Application Job이 playback-events, behavior-events, subscription-events를
읽어 MinIO의 `event-lake` 버킷에 streaming Parquet로 보존한다. 분석 지표를 만들거나
Raw를 중복 제거하지 않는다. Subscription 발행은 6단계 작업이며 지금은 수집 경로만 준비한다.

## 실행

프로젝트 루트에서:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle"
.\backend\gradlew.bat -p backend :event-archive:build
docker compose build archive-jobmanager
docker compose up -d archive-taskmanager
```

Backend 전체는 `backend/`에서 `./gradlew clean build`로 빌드한다. `build`는 배포용 JAR와 의존 라이브러리를 `build/distribution/`에 준비하는 작업도 포함한다.

마지막 명령은 필요한 Kafka, MinIO, 초기화 작업, JobManager를 함께 실행한다.
전체 서비스를 실행할 때도 같은 서비스를 사용한다. IDE 개발 환경에서는
`docker compose -f backend/docker-compose.yml --env-file .env ...`로 실행한다.
Flink UI: http://localhost:8082. 두 Compose를 같은 머신에서 동시에 실행하지 않는다.

Flink 1.20.3 / Kafka SQL connector 3.4.0-1.20 / Java 17 배포물을 사용한다.
Gradle은 기존 JDK 21로 빌드하되 이 모듈만 Java 17 bytecode를 생성한다.
Spring BOM은 이 모듈에 적용하지 않으며, Spring API/공통 JPA 모듈에 의존하지 않는다.

## 저장 구조와 스키마

```text
event-lake/
  raw/playback/dt=YYYY-MM-DD/hour=HH/part-...
  raw/behavior/dt=YYYY-MM-DD/hour=HH/part-...
  raw/subscription/dt=YYYY-MM-DD/hour=HH/part-...
  _flink/checkpoints/...
  _flink/savepoints/...
```

dt/hour는 **Kafka record timestamp 기준 UTC**다. 클라이언트 시계나 occurredAt이
잘못되어도 경로가 과도하게 분산되지 않게 하며, occurredAt/receivedAt 원본은 그대로 보존한다.
도메인은 이벤트 이름을 추측하지 않고 구독한 토픽으로 결정한다.
파일은 Flink 기본 `part-...` 이름을 사용하며 확장자가 없어도 내부 포맷은 Parquet다.

| 컬럼 | 의미 |
|---|---|
| event_id, event_type | JSON에서 추출한 조회 보조값. 파싱 실패 시 null |
| raw_event (BINARY) | Kafka value의 원본 바이트 전체. JSON 재직렬화·필드 제거 없음 |
| kafka_topic, kafka_partition, kafka_offset | 재처리·중복 진단용 원본 위치 |
| kafka_timestamp | Kafka record timestamp, UTC |
| dt, hour | 디렉터리 partition 컬럼 |

미래 버전·알 수 없는 payload 필드·잘못된 JSON도 원본 바이트를 저장한다.
보조 컬럼이 null이어도 버리지 않는다. 정상 JSON은 읽을 때 `CAST(raw_event AS STRING)`으로
복원할 수 있다. 현재 envelope 필드를 물리 Parquet 컬럼으로 모두 고정하지 않아
이벤트 스키마가 확장되어도 원본을 다시 해석할 수 있다.

## Rolling과 checkpoint

- 기본 목표 파일 크기 128 MB, rollover 60초, 시간 확인 주기 10초, checkpoint 60초.
- 여러 레코드를 writer로 기록한다. 이벤트별 object를 생성하지 않는다.
- Parquet는 bulk format이므로 checkpoint가 파일 확정에도 관여한다. 저유량에서는
  목표 크기보다 작은 파일이 생긴다. 실제 파일 수·크기는 데이터량과 checkpoint에 좌우된다.
- 완료된 Raw 파일을 갱신·삭제하지 않는다. checkpoint 경로는 Raw 경로와 분리한다.
- checkpoint는 MinIO에 저장하며 최근 3개를 유지한다. 취소 시에도 보존한다.
  TaskManager 장애는 살아 있는 JobManager가 마지막 완료 checkpoint에서 자동 복구한다.
  fixed-delay 재시도는 10초 간격, 3회다. 컨테이너 프로세스 자체는 운영자가 다시 시작해야 한다.
- 새 Job을 checkpoint 없이 제출하면 Kafka earliest부터 다시 읽는다. 데이터 누락을 피하는 대신
  새 파일에 중복이 생길 수 있다. 재처리 시 eventId와 Kafka 위치를 이용해야 한다.
  checkpoint가 저장됐다는 이유만으로 새 컨테이너가 자동 복원된다고 가정하지 않는다.
- 현재 단일 broker/JobManager 로컬 구성이다. 완전한 장애 무손실·production HA를 보장하지 않는다.

환경 변수는 `.env.example`의 ARCHIVE_* 및 ANALYTICS_*를 참고한다.
MinIO credential은 기존 환경 변수에서 공급하며 코드나 이미지에 포함하지 않는다.
MinIO 콘솔에서 `event-lake/raw/`를 확인하거나 Flink SQL의 filesystem/parquet 테이블로 읽는다.

읽기 예제(기본 경로, 최소 한 번 파일이 생성된 뒤 프로젝트 루트에서):

```powershell
$archiveSql = (Resolve-Path 'backend/event-archive/inspect.sql').Path
docker compose run --rm --no-deps -v "${archiveSql}:/tmp/inspect.sql:ro" archive-taskmanager /opt/flink/bin/sql-client.sh -f /tmp/inspect.sql
```

읽기 작업은 임시 컨테이너의 local mode로 실행한다. Archive Application Cluster에
별도 조회 Job을 제출하거나 실행 중인 JobManager 내부에서 local cluster를 띄우지 않는다.

2단계 확인 결과: 모듈 빌드와 두 Compose 검증 성공. 격리된 Kafka/MinIO/Flink 환경에서
playback 2건, behavior 3건(비정상 JSON 1건 포함), subscription 2건을 Parquet 3개로
저장하고 다시 읽었다. eventVersion=99의 미래 payload 필드도 보존됐으며 checkpoint 완료를
확인했다. 3단계 복원 절차와 검증 범위는 아래를 참고한다. 부하 테스트는 포함하지 않았다.

## 3단계: 장애 복구

Kafka는 중단 중 이벤트를 보관하고, checkpoint는 Kafka offset과 Parquet writer/committer
상태를 함께 복원한다. Kafka consumer group offset만 되돌리는 것으로 대체하지 않는다.
Raw의 `event_id`는 수집 재시도로 중복될 수 있다. 복구 중복 검사는
`(kafka_topic, kafka_partition, kafka_offset)` 기준으로 수행한다.

### TaskManager 장애

```powershell
docker compose start archive-taskmanager
```

JobManager가 살아 있으면 기존 Job의 checkpoint에서 재시도한다. UI의 Checkpoints에서
Restored checkpoint, 이후 Completed checkpoint 증가, Job RUNNING을 확인한다.
재시도 소진으로 FAILED가 되면 아래 명시적 복원을 사용한다.

### JobManager 재생성 / 전체 Flink 중단

이 구성은 단일 JobManager이며 HA metadata 저장소를 두지 않는다. **컨테이너를 새로 만들 때
자동으로 최신 checkpoint를 찾지 않는다.** 첫 실행만 복원 경로를 비워 두고, 기존 작업 복원에는
완료된 snapshot의 URI를 지정한다. 경로가 잘못되거나 상태가 호환되지 않으면 실패하며
earliest로 자동 fallback하거나 복원되지 않은 상태를 무시하지 않는다.

JobManager가 살아 있을 때 최신 완료 경로 확인:

```powershell
$jobs = (Invoke-RestMethod http://localhost:8082/jobs/overview).jobs
if ($jobs.Count -ne 1) { throw '복원 대상 Job을 직접 선택하세요.' }
$jobId = $jobs[0].jid
$checkpoints = Invoke-RestMethod "http://localhost:8082/jobs/$jobId/checkpoints"
$checkpoints.latest.completed.external_path
```

이미 JobManager를 잃었다면 MinIO의 `_flink/checkpoints/<기존-job-id>/`에서 `_metadata`가
있는 checkpoint와 운영 기록을 확인해 복원 경로를 선택한다. 이전 실행의 임의 checkpoint나
불완전한 디렉터리를 고르지 않는다. 오래된 경로는 보존 개수 초과로 삭제됐을 수 있다.
REST에서 미리 복사한 경로 역시 장애 시점의 최신 경로라는 보장은 없다.

계획된 재시작에서는 최신 checkpoint를 복사한 뒤 계속 실행시키지 말고, **stop-with-savepoint**로
기존 Job을 정지하여 확정된 복원 지점을 만든다:

```powershell
docker compose exec archive-jobmanager /opt/flink/bin/flink stop --savepointPath s3://event-lake/_flink/savepoints $jobId
# 출력된 savepoint URI를 아래 값으로 사용한다.
```

복원(동일 코드·토픽·병렬도·MinIO 데이터 사용):

```powershell
$env:ARCHIVE_RESTORE_PATH = 's3://event-lake/_flink/checkpoints/<job-id>/chk-<number>'
docker compose up -d --no-deps --force-recreate archive-jobmanager archive-taskmanager
# 계획 재시작이면 위 변수에 checkpoint 대신 stop 명령의 savepoint URI를 넣는다.
```

Flink REST `/jobs/<새-job-id>/checkpoints`의 `latest.restored.external_path`가 지정 경로인지,
새 checkpoint가 완료되는지 확인한다. 환경 변수/`.env`에 남은 경로는 다음 재생성에도
적용되므로 매번 새 복원 지점으로 갱신한다. 빈 값으로 재생성하면 최초 실행처럼 재수집한다.
`--no-deps`는 Kafka/MinIO가 실행 중인 경우의 명령이다.

### 보장 범위

- 복원 시 Kafka에 checkpoint 이후 offset이 남아 있고 MinIO 데이터가 유지되어야 한다.
  테스트는 Kafka/MinIO를 유지한 상태에서 Flink만 중단한다. Kafka/MinIO 컨테이너나 디스크
  동시 손실, retention 만료, production HA는 이번 범위에 포함하지 않는다.
- 이미 파일이 확정된 시점보다 오래된 checkpoint로 복원하면 Raw 중복이 생길 수 있다.
  장애 복구 시점이 불확실하거나 코드/토픽/병렬도를 변경할 때는 무손실·무중복을 단정하지 않는다.
- MinIO Raw는 장기 backfill 원천이다. 삭제 후 재수집으로 복구하지 않는다.
  운영 데이터에 `docker compose down --volumes`를 실행하지 않는다.
- `inspect.sql`의 `row_count = unique_positions = raw_rows`로 입력 위치별 중복과 원본 존재를
  확인한다. 누락은 Kafka 입력 건수/offset 범위와 별도로 대조해야 한다.

### 3단계 확인 결과 (2026-09-23)

격리된 Compose 프로젝트에서 checkpoint 주기를 10초, 토픽 partition을 1개로 설정해 수행했다.
세 토픽 각각에 장애 전 1건, TaskManager SIGKILL 중 1건, JobManager SIGKILL 중 1건을 발행했다.

1. TaskManager 재시작 후 기존 Job이 checkpoint에서 복구되고 새 checkpoint가 완료됐다.
2. JobManager 종료 직전 완료 checkpoint URI를 기록하고, 두 Flink 컨테이너를 재생성했다.
   REST의 restored 경로가 지정한 MinIO URI와 일치했고 이후 checkpoint 6회 완료, 실패 0회였다.
3. 별도 SQL reader로 실제 Parquet를 읽었다. 세 토픽 모두 row_count=3,
   unique_positions=3, raw_rows=3으로, 입력 총 9건의 누락·위치 중복이 없었다.
4. 문서의 stop-with-savepoint 명령으로 MinIO savepoint 생성과 Job 정지도 확인했다.

이는 작은 입력과 두 장애 시나리오의 결과이며 모든 장애 시점에 대한 exactly-once 증명은 아니다.
TaskManager 중단 당시에는 checkpoint 실패가 발생했으며 복구 후 완료가 재개됐다.
두 Compose config 검사와 이미지 빌드도 통과했다. 관계없는 API/Frontend 전체 테스트는 반복하지 않았다.

설계 참고: [Flink filesystem sink](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/filesystem/),
[S3 filesystem plugin](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/deployment/filesystems/s3/).
