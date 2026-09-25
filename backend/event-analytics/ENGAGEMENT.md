# 5단계: Watch Time / Completion / Retention / Episode Conversion

기존 Analytics Job의 playback Kafka source에 관측값 projection과 전용 ClickHouse sink를 연결한다.
별도 인프라·Frontend 호출은 추가하지 않는다. Flink가 watch delta와 시청 구간을 정규화하고,
ClickHouse가 중복 제거 후 세션/사용자/콘텐츠 단위로 계산한다. 원본은 기존 MinIO Archive에 남는다.

## 입력 보강

Collector의 기존 세션 조회에서 서버가 `playbackContext`를 추가한다:
`durationMs`, `episodeId`, `seasonNumber`, `episodeNumber`, `nextEpisodeId`.
브라우저 DTO에는 이 필드를 추가하지 않으며, payload에 같은 이름을 넣어도 서버 context를 대체하지 못한다.
MediaPackage 길이와 catalog의 `(seasonNumber, episodeNumber)` 순서를 사용하므로 번호에 공백이 있거나
다음 시즌으로 넘어가는 경우에도 다음 에피소드를 찾는다. 작품/마지막 에피소드는 다음 ID가 null이다.
다음 에피소드는 catalog에 등록된 순서이며, 국가별 공개 상태·이용권별 재생 가능성을 의미하지 않는다.
DB 조회와 Kafka 발행 사이에 DB transaction을 유지하지 않는다.

Envelope eventVersion=1에 optional context를 더한 호환 변경이다. 기존 이벤트는 payload.durationMs로
시청 지표를 계산하되 `catalog_enriched=0`, episode_id=0으로 표시하여 에피소드 전환에서는 제외한다.
서버 context가 있으면 클라이언트 duration을 사용하지 않는다. 0은 길이 미확정이며 완주/유지율에서 제외한다.

## 계산 정의

| 지표 | 정의 |
|---|---|
| Watch time | `playedMsSincePreviousEvent` 합계. 실제 경과 시청 시간이며 pause/buffering/seek 이동 거리는 포함하지 않는다. 재시청은 가산한다. |
| 시청 구간 | `[end - covered, end)`로 정규화. 일반 이벤트 end=positionMs, SEEK end=fromPositionMs. covered는 전진 거리와 `playedMs × playbackRate` 중 작은 값. 영상 길이로 경계를 제한한다. |
| 세션 완주 | 구간의 합집합 길이 / duration ≥ 90%. 끝으로 seek하거나 ended 이벤트만 보내도 완주하지 않는다. |
| 완주율 | 완주 세션 / 시작 이벤트가 있고 duration>0인 세션. 분모 0이면 null. 아직 진행 중인 세션도 분모에 포함된다. |
| 평균 시청 시간 | 해당 cohort의 총 watch time / 관측된 세션 수. 단위 초. 사용자당 평균과 구분한다. |
| Retention | 0–10%, …, 90–100%의 10개 구간별로 실제 관측 구간이 조금이라도 겹친 세션 수 / 시작·길이를 아는 세션 수. 세션당 각 구간은 한 번만 센다. |
| Episode conversion | 실제 시청이 있는 사용자/에피소드의 첫 시작을 기준으로, 그 뒤 7일 이내 다음 catalog 에피소드에서 시작 이벤트와 양의 시청 시간을 관측한 사용자 비율. |

Retention은 구간 방문율이므로 곡선이 반드시 단조 감소하지 않는다. 건너뛴 중간 구간은 방문하지 않은 것으로 남는다.
시청 구간은 현재 수집 중인 endpoint·delta 기반 추정이다. 샘플 유실·누락된 seek·rate 변경 경계는
정확한 구간을 재구성할 수 없으며, rate가 바뀐 이벤트에서는 해당 이벤트 rate로 거리 상한을 계산한다.
장시간 이벤트 유실을 다음 position으로 메우거나, 누락된 시간을 실제 시청으로 추측하지 않는다.
클라이언트 관측 기반 통계이며 부정 시청 판정/과금의 근거로 사용하지 않는다.

중복은 먼저 eventId, 다음 `(SHA-256(playbackSessionId), sequence)`로 제거한다.
동일 identity의 내용은 불변 계약이다. 같은 sequence에 상충하는 UUID가 있으면 작은 UUID를 선택한다.
재생 세션 토큰 원문은 serving 테이블에 넣지 않는다. 순서가 뒤바뀌거나 늦게 도착해도 조회 시 다시 계산한다.
세션 시작 이벤트가 없으면 관측 시청 시간은 남기지만 완주·유지율·전환 분모에는 포함하지 않는다.

일별 지표는 **UTC 세션 시작일 cohort**다. 시작 이벤트가 없으면 첫 관측일로 묶는다.
자정을 넘긴 세션의 모든 시청 시간은 시작일에 귀속하며 달력 날짜별 실제 소비 시간과 다르다.
에피소드 전환은 사용자/에피소드 첫 시작일 cohort이고, 다음 에피소드의 이전 시청은 전환으로 세지 않는다.
7일이 지나기 전 수치는 잠정치이며 `cohort_mature`로 구분한다. 이후 지연 이벤트로도 결과가 바뀔 수 있다.
마지막/다음 ID 미확정 에피소드는 전환율 분모에서 제외한다. 시청 종료 여부/공개 여부로 cohort를 추가 제한하지 않는다.

## 조회 테이블과 view

- `playback_observations`: 지표에 필요한 관측값만 저장하는 물리 테이블. 직접 합산 금지.
- `playback_observations_current`: eventId + session/sequence 중복 제거 결과.
- `playback_sessions`: 세션별 watch_ms, covered_ms, duration_ms, 시작·에피소드 정보.
- `viewer_video_watch`: 사용자/영상 누적 watch_ms와 중복을 합친 watched_fraction.
- `content_daily_engagement`: cohort별 watch_hours, average_watch_seconds, completion_rate.
- `content_retention`: cohort·콘텐츠별 10% bucket retention_rate.
- `episode_conversion`: cohort·출발/다음 에피소드별 conversion_rate, cohort_mature.

비율은 0~1이다. 여러 행의 비율을 단순 평균하지 말고 노출된 분자·분모를 합쳐 계산한다.
현재 view는 조회 시 집계한다. 누락·중복·재생순서가 다른 입력에 정확한 기준 구현을 제공하는 범위이며,
대규모 처리 성능, 장기 session array 크기, FINAL 비용은 부하 측정 후 사전 집계로 개선할 대상이다.
여러 언어/편집본의 동일 영상 의미를 합치는 정책은 추가하지 않았다. 실제 시청 구간은 videoId별로 계산한다.

## 적용 / 이전 데이터

```powershell
# 프로젝트 루트
$env:GRADLE_USER_HOME = "$PWD\.gradle"
.\backend\gradlew.bat -p backend :user-api:bootJar :event-analytics:analyticsDistribution
docker compose build user-api analytics-jobmanager
docker compose up -d clickhouse-init
docker compose up -d user-api
docker compose up -d --no-deps --force-recreate analytics-jobmanager analytics-taskmanager
```

API를 IDE에서 실행 중이면 user-api를 재시작한다. 초기화 작업은 001, 신규 002 SQL 순서로 실행한다.
기존 001 SQL/테이블은 변경·삭제하지 않는다. Docker full-stack 외에 backend Compose도 동일 migration을 사용한다.

**4→5 첫 적용** 시 기존 4단계 Job을 먼저 정지하고 `ANALYTICS_RESTORE_PATH`를 비운 상태로 재수집한다.
새 engagement 분기는 예전 checkpoint 이전 레코드를 이미 처리한 적이 없으므로, 이전 source offset을
복원하면 그 구간이 자동 backfill되지 않는다. Kafka에 남은 기록만 earliest 재생하며 4단계 지표는 중복 제거된다.
운영 중인 Job을 중복 실행하지 않는다. Kafka retention 이전 데이터는 별도 MinIO backfill 작업이 필요하다.
context가 없는 과거 이벤트의 에피소드 관계를 임의로 추측하지 않는다.
5단계 이후 장애 복원에는 평소와 같이 완료된 5단계 checkpoint/savepoint URI를 사용한다.

## 검증 재현

```powershell
# 기존 서비스를 중지한 로컬에서, 비어 있는 격리 프로젝트에만 사용
$env:ANALYTICS_CHECKPOINT_INTERVAL = '10 s'
$env:ANALYTICS_PARTITIONS = '1'
docker compose -p ott-analytics-stage5-check up -d analytics-taskmanager
.\backend\event-analytics\verify-engagement.ps1
# 전체 재수집 후 같은 기대값인지 확인할 때는 -VerifyOnly
docker compose -p ott-analytics-stage5-check down --volumes
```

검증 스크립트는 비어 있지 않은 observations 테이블에는 샘플을 발행하지 않는다.
16개 Kafka record를 입력하고 14개 고유 session sequence로 정리한 결과를 검사한다.
시청 280초, 평균 93.333초, 완주 2/3, 60% bucket 유지 2/3, 90% bucket 유지 1/3,
에피소드 전환 1/2가 기대값이다. 중복·역순·seek·재시청·2배속·길이 0·시작 누락·자정 경계를 포함한다.
Java 단위 테스트와 실제 MySQL 조회 테스트에서 시즌 경계/번호 공백/권한·만료 조건도 확인한다.

2026-09-23 검증 완료: 위 16건 적재 및 전체 재수집 후 기대값 모두 일치했다.
재수집 Job은 checkpoint 3회 완료/실패 0회였고, Analytics 단위 테스트 13개와
실제 MySQL 조회 테스트 1개, Collector/Publisher/Kafka 관련 테스트를 통과했다.
user-api JAR·Analytics 이미지 빌드와 두 Compose 검증을 완료했으며 테스트 전용 환경은 정리했다.

구간 합집합 계산은 [ClickHouse intervalLengthSum](https://clickhouse.com/docs/sql-reference/aggregate-functions/reference/intervalLengthSum)을 사용한다.
