# 7단계 — 재생 품질(QoE)

사용자 플레이어 → 인증된 Analytics API → playback-events → 기존 Flink Analytics Job의
QoE 분기 → ClickHouse. 새 서비스나 별도 브로커를 추가하지 않는다.
기존 Engagement·구독·이어보기 경로는 유지한다.

## 측정 계약

새 플레이어는 payload에 `qoeVersion: 1`을 표시한다. 이전 플레이어의 초기 로딩·탐색·
일시정지 버퍼링 측정과 섞이지 않도록 이 표시가 없는 이벤트는 QoE 집계에서 제외한다.
기존 이벤트는 기존 분석과 Raw Archive에 계속 수집된다. envelope eventVersion은 1이다.

| 지표 | 정의 |
|---|---|
| 시작 지연 | 첫 `play` 이벤트부터 첫 `playing` 이벤트까지의 performance.now 차이. 재생 버튼을 누르기 전 대기 시간은 제외. 초기 재생을 취소하고 다시 누르면 다시 측정 |
| 평균·p95 시작 지연 | 시작 이벤트와 startupTimeMs가 있는 세션만 집계. 누락은 NULL, 실제 0ms는 포함. p95는 ClickHouse 근사 분위수 |
| 버퍼링 | 첫 재생 이후, 재생 중이며 seek가 아닌 상태에서 발생한 waiting부터 재개까지. 중복 waiting은 한 번만 기록 |
| 중단된 버퍼링 | pause·seek·종료·치명적 오류까지 관측된 대기 시간을 BUFFER_ENDED로 기록. 이후 사용자의 대기 시간은 제외 |
| 버퍼링 비율 | 관측 버퍼링 시간 / (관측 버퍼링 시간 + 실제 진행된 시청 시간). 시작 로딩·일시정지·탐색 시간 제외. 배속에서도 벽시계 시간 기준 |
| 버퍼링 세션 비율 | 버퍼링을 관측한 재생 세션 / PLAY가 관측된 세션. 양쪽 모두 세션 시작 이벤트 필요 |
| 오류 세션 비율 | 하나 이상 PLAYBACK_ERROR가 있는 세션 / 시작 이벤트가 있는 세션. 복구 가능한 HLS 오류도 포함 |
| 치명적 오류 비율 | fatal=true가 있는 세션 / 시작 이벤트가 있는 세션. media 오류는 치명적 오류로 표시 |
| 재생 전 실패 | PLAY 없이 치명적 오류가 발생한 세션. 시작 이벤트 필요 |

시작 지연은 브라우저의 `playing` 관측 기반이며 실제 첫 화면 표시 시각이나 API 승인 시간을
측정하는 값이 아니다. 세션 시작은 플레이어 초기화 때 수집하므로 오류율 분모에는 아직
재생 버튼을 누르지 않은 초기화 세션도 포함된다. 재생 시도 실패율과 동일하게 해석하지 않는다.

API는 QoE 시간 필드의 0..86,400,000ms 정수 범위와 오류의 source/code/fatal을 검증한다.
QoE 오류에는 `hls`/`media`, 64자 이내 영숫자·밑줄 코드, boolean fatal만 사용한다.
원본 오류 메시지·미디어 URL·토큰은 serving 테이블에 저장하지 않는다.
playbackSessionId는 기존 분석과 같은 SHA-256 키로 바뀐다. QoE 테이블에는 사용자 ID도 없다.

## 조회

`004-qoe.sql`에 다음 테이블·view가 있다.

- `qoe_events`: 버전이 확인된 품질 관측값. ReplacingMergeTree, eventId 기준 중복 제거.
- `qoe_events_current`: FINAL 적용 후 session_key + sequence 중복 제거. 동일 sequence 충돌은 가장 작은 UUID를 선택하며, 상충한 사실 발행은 지원하지 않는다.
- `qoe_sessions`: 세션별 품질 지표. startup은 sequence상 첫 유효 측정값을 사용한다.
- `content_daily_qoe`: 콘텐츠별 UTC 세션 시작일 cohort. 자정을 넘어도 같은 cohort에 포함된다.
- `qoe_error_breakdown`: 오류 발생일·콘텐츠·source·code·fatal별 이벤트 수와 영향 세션 수.

```sql
SELECT * FROM analytics.content_daily_qoe
WHERE cohort_date >= today() - 7 ORDER BY cohort_date, content_id;

SELECT * FROM analytics.qoe_error_breakdown
ORDER BY event_date DESC, error_events DESC;

SELECT * FROM analytics.qoe_sessions
WHERE buffer_count > closed_buffer_count;
```

시작 이벤트가 없는 세션은 observed_sessions에는 보이지만 정상 지표의 분모·분자에서 제외된다.
버퍼 종료 이벤트를 받지 못한 세션은 open_buffer_sessions에 표시한다. 아직 관측하지 못한
대기 시간을 임의로 추정하지 않으므로 버퍼링 시간은 진행 중이거나 종료 전송이 유실되면
과소 집계될 수 있다. 종료와 시작이 각각 유실되면 이 카운터만으로 모든 누락을 탐지할 수는 없다.
미전송 클라이언트 이벤트의 복원은 보장하지 않는다. 종료 전송은 기존 bounded/keepalive 정책이다.

늦게 도착한 이벤트는 조회 결과에 반영된다. Kafka 재시도·재수집은 동일 eventId/sequence로
중복 제거되며, Kafka→ClickHouse 전송 자체는 at-least-once다. 조회 시 집계이므로 운영 규모의
조회 비용·지연은 별도로 측정해야 한다. `qoe_events`를 FINAL 없이 직접 합산하지 않는다.

## 적용

프로젝트 루트에서 기존 `.env` 설정을 사용한다. 실행 중인 분석기를 갱신할 때 복원 지점은
[기존 운영 문서](README.md)의 checkpoint/savepoint 절차를 따른다.

```powershell
$env:GRADLE_USER_HOME = "$PWD/.gradle"
./backend/gradlew.bat -p backend :event-analytics:test :event-analytics:analyticsDistribution :user-api:test --tests 'com.domain.backend.analytics.*'
docker compose build analytics-jobmanager
docker compose run --rm clickhouse-init
docker compose up -d --force-recreate analytics-jobmanager analytics-taskmanager
```

User API와 user-web도 변경 코드를 반영해 재시작/재빌드한다. 위 명령은 분석기만 갱신한다.
기존 SQL은 수정하지 않았고 새 마이그레이션을 추가했다. 새 QoE 연산자의 UID는 고정이다.
새 QoE 데이터는 수정된 플레이어를 배포한 시점부터 쌓인다.

## 검증 기록 — 2026-09-25 KST

- 프론트 분석 테스트 23개: 시작 지연, 초기 대기 제외, 버퍼 종료, seek/pause, 오류, 중복 종료, 기존 전송/인증 테스트 통과.
- Backend 관련 테스트 39개: Analytics API·Kafka·세션 조회 19개, 분석 projection/sink 20개 통과.
- user-web 타입 검사·변경 파일 ESLint·Next.js production build 통과.
- 별도 Compose 프로젝트에서 Kafka → Flink → ClickHouse 실제 적재 검증 통과.
- 테스트 원본 22건 중 QoE 유효 입력 20건, 중복 제거 후 18개 session sequence. 이전 버전 1건 제외, 잘못된 버퍼 시간 1건 거절.
- 시작 세션 5개, startup 샘플 3개(0/1000/3000ms), 평균 1333.33ms, p95 2800ms.
- 시청 20초, 버퍼링 2초 → 비율 9.09%. 오류 세션 비율 40%, 치명적 오류 세션 비율 20%.
- 버퍼 미종료, 시작 이벤트 누락, 자정 통과, 역순 전송, 중복 eventId/sequence 검증.
- Flink 재시작 후 새 Job이 22건 전체를 재소비하고 QoE sink에 20건을 전달한 것을 연산자 카운터로 확인. 재수집 후 지표 동일.

위 수치는 합성 테스트 데이터의 정합성 검증값이며 실제 사용자 품질·부하 성능 수치가 아니다.
실제 브라우저 네트워크 제한을 이용한 영상 품질 시험은 이번 검증에 포함하지 않았다.

독립된 빈 테스트 환경을 실행한 뒤 다음 스크립트로 재현한다. 기본 프로젝트 이름은
`ott-analytics-stage7-check`이며 실제 사용 중인 데이터 환경에 fixture를 넣지 않는다.

```powershell
docker compose -p ott-analytics-stage7-check up -d analytics-taskmanager
./backend/event-analytics/verify-qoe.ps1
# 이미 입력된 데이터의 집계만 다시 확인
./backend/event-analytics/verify-qoe.ps1 -VerifyOnly
```
