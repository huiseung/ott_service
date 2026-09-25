# Analytics

1단계 Batch 수집은 아래를 참고한다. 2단계 Kafka → Flink → MinIO Parquet Archive의
실행·스키마·checkpoint 범위는 [event-archive/README.md](backend/event-archive/README.md)에 정리했다.

## 범위와 흐름

```text
user-web AnalyticsClient (메모리 큐)
  → POST /api/analytics/events/batch (user-api)
  → playback-events / behavior-events (이벤트당 Kafka record 1개)
  → Flink Archive → MinIO Parquet Raw Archive
  → [후속 단계] Flink Analytics / ClickHouse

재생 위치 관련 이벤트
  → 기존 watch-events → playback-worker → Redis → MySQL watch_history
```

Collector는 기존 user-api 안에 있다. 새 API 서비스나 DB 테이블은 추가하지 않는다.
이어보기 호환용 watch-events는 Raw 이벤트와 별도로 발행한다. 기존 개별 이벤트 API는
호환성을 위해 남아 있지만 user-web은 더 이상 호출하지 않는다. Admin Preview는 계측하지 않는다.

## 현재 계측

- 플레이어: PLAYBACK_SESSION_STARTED, PLAY, PAUSE, HEARTBEAT, SEEK,
  BUFFER_STARTED/ENDED, QUALITY_CHANGED(hls.js), PLAYBACK_RATE_CHANGED,
  PLAYBACK_ENDED, PLAYBACK_ERROR, PLAYBACK_SESSION_ENDED.
- 홈 콘텐츠 카드: 50% 이상 화면에 보일 때 CONTENT_IMPRESSION(카드 mount당 1회), CONTENT_CLICK.
- 상세 조회·검색·찜·예고편·구독 CTA는 수집 계약만 준비한다. 해당 사용자 화면은 새로 만들지 않는다.
- SUBSCRIPTION_ACTIVATED, PAYMENT_SUCCEEDED 등 backend business event는 이 API에서 받지 않는다.

`playedMsSincePreviousEvent`는 관측된 미디어 위치 증가와 경과 시간 중 작은 값으로 누적한
실제 경과 시청 시간이다(배속으로 미디어 위치 증가량을 보정). 정지·seek·buffering 시간은 제외한다.
`positionMs`, `previousPositionMs`, SEEK의 `fromPositionMs/toPositionMs`, `playbackRate`를 함께 보존한다.
브라우저 샘플 간 측정이므로 완벽한 시청 증명은 아니며, 분석 지표를 미리 계산해 넣지 않는다.
`startupTimeMs`는 첫 play 요청부터 첫 playing까지, `bufferingDurationMs`는 waiting부터 playing까지다.
native HLS에서는 hls.js 품질 변경 정보가 없을 수 있다.
브라우저 구분을 위해 payload에 길이를 제한한 userAgent도 함께 기록한다.

## Batch 계약

```json
{
  "events": [{
    "eventId": "b47b724e-56e0-4ab8-9dfa-22af3c604b51",
    "eventType": "HEARTBEAT",
    "eventVersion": 1,
    "occurredAt": "2026-09-23T00:00:00Z",
    "anonymousId": "efb28f22-c80e-43be-bdf5-1ca8bb30f901",
    "sessionId": "faf8d701-9a30-4b87-ae23-2678e45e02bd",
    "producer": "user-web",
    "platform": "WEB",
    "playbackSessionId": "재생 API에서 받은 세션 ID",
    "sequence": 2,
    "videoId": 10,
    "payload": {
      "positionMs": 10000,
      "previousPositionMs": 0,
      "playedMsSincePreviousEvent": 10000,
      "durationMs": 60000,
      "playbackRate": 1,
      "ended": false
    }
  }]
}
```

서버가 `receivedAt`, 인증된 `userId`를 부여한다. Playback의 videoId/contentId는
본인 소유의 유효한 PlaybackSession과 DB 연결 관계에서 확인·보강한다.
클라이언트의 userId를 신뢰하지 않으며, 익명 요청에는 행동 이벤트만 허용한다.
Behavior contentId와 payload는 클라이언트 관측값이며 결제·권한 판정의 근거가 아니다.
anonymousId는 브라우저 localStorage, sessionId는 탭 sessionStorage에 저장한다.
스토리지 차단 시 페이지 수명 동안의 ID로 동작한다. 이벤트 큐 자체는 디스크에 저장하지 않는다.

- 최대 50개 / HTTP body 64 KiB. 빈 batch, 잘못된 버전·이벤트 타입·필수 사실은 400.
- 서버는 역직렬화 전에 Content-Length가 없는 요청까지 크기를 제한한다(413).
- 한 batch 내부 중복 ID는 400. 서로 다른 재시도 요청 사이의 같은 ID는 허용한다.
- 전체 batch를 검증한 후 발행한다. 권한 실패는 401/403이며 이 경우 아무것도 발행하지 않는다.
- 모든 Raw 및 이어보기 레코드의 Kafka ACK 이후 `202 { "acceptedEventIds": [...] }`.
- Kafka 실패 또는 publisher 작업 큐 포화는 503. 202 전에는 영속 수집 완료로 간주하지 않는다.

## 전송·중복·장애 정책

- heartbeat 10초, 주기 flush 25초, 50개 또는 약 48 KiB에서 조기 flush.
- body 크기는 UTF-8 바이트로 계산한다. 요청은 순차 처리하며 누적 backlog는 batch 단위로 보낸다.
- 메모리 큐 상한 500개 / 512 KiB, 유효 기간 5분, 네트워크·408·429·5xx는 최대 5회 시도.
  재시도 대기 하한은 1/2/4/8초 등으로 증가하며 실제 시도는 다음 flush 시점에 이루어진다.
- 큐가 찼거나 단일 이벤트가 너무 크면 새 이벤트를 버린다. 400/401/403/413 등 영구 오류는 버린다.
  실패·폐기 카운터는 `getAnalytics().stats`에서 확인 가능하다. 분석 오류는 재생 UI에 전파하지 않는다.
- 이벤트 생성 당시 Bearer 토큰별로 batch를 분리한다. 로그인 뒤 익명 이벤트를 사용자 이벤트로
  바꾸거나 로그아웃 전 이벤트를 다음 사용자에게 귀속시키지 않는다. 긴 재생 중 토큰 만료 시
  현재 사용자가 동일한 경우에만 기존 refresh API로 갱신해 한 번 재전송한다.
  갱신 전후 계정이 다르면 재전송하지 않는다. 갱신 실패로 사용자 인증 상태를 지우지 않는다.
  pagehide에서는 인증 갱신을 시도하지 않으며 최종 401 이벤트는 폐기한다.
- pagehide 시 플레이어 마지막 사실을 먼저 기록하고 keepalive batch 하나를 보낸다.
  in-flight 이벤트도 같은 ID로 재전송할 수 있다. 브라우저 종료 시 ACK·전체 backlog 전송은 보장하지 않는다.
- Kafka key: Playback은 playbackSessionId, Behavior는 sessionId. 동일 세션의 sequence를 보존한다.
  동시 클라이언트·pagehide·부분 실패 재시도에 따른 중복 및 순서 역전은 가능하다.
- batch는 Kafka transaction이 아니다. 일부 발행 뒤 실패하면 전체를 동일 eventId로 재시도한다.
  Producer idempotence가 HTTP 재시도 중복을 제거하지는 않는다. 후속 분석은 eventId 중복 제거와
  playbackSessionId + sequence 정렬을 전제로 한다. 기존 진행률 consumer는 sequence 갱신 규칙을 사용한다.
- DB 조회가 완료된 뒤 별도 bounded executor에서 Kafka I/O를 수행한다. DB transaction 안에서 발행하지 않는다.
- 2단계 Archive가 실행 중이면 Raw를 MinIO에도 보존한다. 장애 복원 검증과 backfill 실행 경로는 후속 단계다.

## 로컬 설정과 검증

토픽 환경 변수: `ANALYTICS_PLAYBACK_TOPIC`, `ANALYTICS_BEHAVIOR_TOPIC`, `ANALYTICS_PARTITIONS`.
기본값은 각각 playback-events, behavior-events, 12이며 user-api의 KafkaAdmin이 토픽을 만든다.
로컬 단일 broker이므로 replication factor는 1이다.

Kafka 접속 주소는 IDE/JVM에서 `localhost:9092`, Compose 안에서 `kafka:29092`다.
두 Compose의 역할은 유지한다. 기존 실행 환경에 listener 변경을 적용하려면 Kafka와 관련
API/Worker 컨테이너를 재생성해야 한다. 볼륨 삭제는 필요하지 않다.

```powershell
# 프로젝트 루트
$env:GRADLE_USER_HOME = "$PWD\.gradle"
.\backend\gradlew.bat -p backend :user-api:test --tests 'com.domain.backend.analytics.*' --tests 'com.domain.backend.user.application.PlaybackServiceTest' :playback-worker:test
docker compose config --quiet
docker compose -f backend/docker-compose.yml --env-file .env config --quiet

cd frontend/apps/user-web
node --experimental-strip-types --test src/features/analytics/*.test.mjs
node node_modules/typescript/bin/tsc --noEmit
pnpm lint
```

Backend JAR을 다시 빌드한 뒤 실행하는 방법은 README를 따른다.
Kafka 중단 중에도 영상 재생은 계속되며 Analytics 큐만 제한적으로 재시도한다.
Raw Archive 확인 및 3단계 checkpoint 복원 방법은
[Archive 운영 문서](backend/event-archive/README.md)를 따른다.
4단계의 독립 Flink → ClickHouse 기본 지표 적재·조회·복원은
[Analytics 운영 문서](backend/event-analytics/README.md)를 따른다.
5단계 시청 시간·완주율·유지율·에피소드 전환 정의와 조회는
[Engagement 문서](backend/event-analytics/ENGAGEMENT.md)를 따른다.
6단계 테스트 구독 완료·Outbox·콘텐츠 전환 기여는
[Subscription 문서](backend/event-analytics/SUBSCRIPTIONS.md)를 따른다.
7단계 재생 시작 지연·버퍼링·오류 지표 정의와 조회는
[QoE 문서](backend/event-analytics/QOE.md)를 따른다.
8단계 관리자 분석 화면·조회 API의 실행과 검증은
[Admin Analytics UI 문서](backend/event-analytics/ADMIN_UI.md)를 따른다.

Kafka JSON 직렬화기는 프로젝트에 설치된 Spring Kafka의 JacksonJsonSerializer /
JacksonJsonDeserializer를 사용한다. 기존 Jackson 2 직렬화기에서 누락된 Instant 지원을
해결했으며, Raw JSON과 watch-events 역직렬화 호환성을 Kafka 통합 테스트로 검증한다.



## 리팩토링
- 단일 파일의 코드가 약 400줄을 초과하면 책임 분리가 가능한지 검토한다.
- 단순히 줄 수를 줄이기 위한 분리는 하지 않는다.
- 클래스가 여러 변경 이유 또는 서로 다른 도메인 책임을 가진 경우,
  줄 수와 관계없이 별도 컴포넌트로 분리한다.

## 커밋
- 한국어로 작성한다
- 하나의 커밋은 다른 사람이 리뷰하기 좋게 변경이 200줄 이내로 있는걸 지향한다
- 다음과 같이 타입과 제목을 작성한다
```
# 타입 : <제목>
# feature : 기능 개발
# fix : 버그 수정
# refactor : 리팩토링 
# test : 테스트 작성
# config : 설정
# docs : 문서
# style : css
```
- 본문 내용에 코드 변경사항에 대한 설명을 간략하게 적는다