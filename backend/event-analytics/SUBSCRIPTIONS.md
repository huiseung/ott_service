# 6단계: 테스트 구독 / Transactional Outbox / Content Attribution

사용자 요청에 따라 **실제 결제 없이 버튼 클릭으로 테스트 구독을 완료**한다.
홈 콘텐츠 카드의 로그인 사용자에게 `테스트 구독하기 (결제 없음)` 버튼을 제공한다.
기존 콘텐츠 상세/결제 화면이 없으므로 현재 홈 카드 흐름에 연결했으며 `surface=home`으로 기록한다.
완료 후 실제 결제가 없었다고 표시한다. 카드·Frontend가 구독 활성화 이벤트를 직접 발행하지 않는다.

## 동작

1. Analytics Client가 `SUBSCRIPTION_CTA_CLICK`을 큐에 넣고 eventId를 반환한다.
2. `POST /api/user/subscriptions/checkouts`: requestId, contentId, ctaEventId로 checkout 생성.
3. `POST /api/user/subscriptions/local/checkouts/{checkoutId}/activate`: 소유자 인증 후 테스트 활성화.
4. DB 상태와 Outbox를 같은 트랜잭션으로 저장한 뒤 성공 응답. Kafka 대기/HTTP 전송은 없다.
5. 별도 scheduled relay가 저장된 JSON을 subscriptionId key로 Kafka에 전송한다.
6. 기존 Archive는 원문을 보존하고 Analytics Job은 별도 subscription consumer group으로 ClickHouse에 투영한다.

처음 구독 신청 시 SUBSCRIPTION_CREATED, CHECKOUT_STARTED를 기록하고,
활성화 시 SUBSCRIPTION_ACTIVATED, 취소 시 SUBSCRIPTION_CANCELLED를 기록한다.
모든 구독 이벤트는 producer=user-api, platform=SERVER, activationSource=LOCAL_TEST다.
PAYMENT_SUCCEEDED 이벤트/실제 결제금액/카드 정보는 생성하지 않는다.
자동 갱신·만료·환불·유료 결제 검증·구독에 따른 재생권한 변경은 이번 범위에 포함하지 않는다.

`GET /api/user/subscriptions/me`는 NONE/PENDING/ACTIVE/CANCELLED 상태를 조회하며,
`POST /api/user/subscriptions/cancel`은 현재 활성 구독을 취소한다. API는 모두 로그인 사용자 범위다.
checkout은 생성 후 30분까지 활성화할 수 있다. 같은 requestId의 재요청은 기존 checkout을 반환한다.
동일 requestId로 contentId/ctaEventId를 바꾸면 409다. 성공한 checkout 재활성화와 중복 취소는 이벤트를 추가하지 않는다.
user row lock과 unique key로 동시 클릭을 직렬화한다. 사용자당 구독 identity는 유지하며,
취소 후 재활성화는 `firstActivation=false`로 기록해 신규 구독 전환과 구분한다.

## Outbox 보장과 운영

V10 Flyway migration이 user_subscriptions, subscription_checkouts, subscription_outbox를 추가한다.
프로젝트에 기존 Outbox가 없어 user-api 안에 이 용도에 필요한 최소 구현을 두었다.

- 원자성: 구독/checkout 변경과 Outbox 저장이 모두 commit되거나 모두 rollback된다.
- claim: 짧은 DB transaction에서 `FOR UPDATE SKIP LOCKED`, 최대 20건, 120초 lease.
- 같은 subscription의 이전 event가 SENT가 되기 전 다음 event는 claim하지 않는다.
- claim transaction이 종료된 다음 Kafka ack를 기다린다. 성공 후 짧은 새 transaction으로 SENT 표시.
- 실패는 제한된 backoff로 최대 8회. 최종 실패는 FAILED로 남긴다. 삭제하거나 성공 처리하지 않는다.
- 프로세스 종료 후 만료 lease를 다시 claim하며 claim token으로 오래된 worker의 ack를 차단한다.
- Kafka 성공 후 DB ack 전에 죽으면 재발행될 수 있다. eventId/JSON은 바뀌지 않고 ClickHouse FINAL로 중복을 제거한다.
- 이전 FAILED event는 같은 구독의 후속 event도 막는다. 원인 복구 후 해당 row를 다시 대기 상태로 바꿔 순서대로 재생한다.

운영 확인 SQL(실제 row를 확인한 후 재시도 대상으로 event_id를 지정한다):

```sql
SELECT status, COUNT(*) FROM subscription_outbox GROUP BY status;
SELECT id, event_id, aggregate_id, event_type, attempts, last_error_code
FROM subscription_outbox WHERE status='FAILED' ORDER BY id;
-- FAILED만 재시도한다. 성공 이벤트의 identity/payload를 바꾸지 않는다.
UPDATE subscription_outbox
SET status='PENDING', attempts=0, available_at=UTC_TIMESTAMP(6), claim_token=NULL
WHERE event_id='<확인한 event UUID>' AND status='FAILED';
```

현재 SENT row 보존/정리는 자동화하지 않았다. relay 로그에는 ID/횟수만 남기며 원문이나 인증 정보를 출력하지 않는다.
`SUBSCRIPTION_OUTBOX_ENABLED=false`면 발행을 중지하고 DB에 대기시킨다. 다시 켜면 이어서 발행한다.

## 콘텐츠 기여 정의

자동 last-touch 추측 대신 **checkout에 명시적으로 연결한 CTA**를 사용한다.
신규 activation의 subscriptionId/checkoutId/사용자가 일치하는 서버 CHECKOUT_STARTED를 찾고,
ctaEventId·사용자·contentId가 모두 일치하는 인증된 CTA를 연결한다.
CTA occurredAt은 checkout 시각 이전 7일 이내, activation은 checkout 이후 30분 이내여야 한다.
같은 사용자의 서로 다른 콘텐츠 클릭을 임의로 귀속시키지 않는다.

Frontend batch가 늦게 도착해도 조회 시 join하므로 귀속 결과가 갱신된다.
CTA가 누락/유실되거나 user/content/time이 맞지 않으면 **content_id=0 (미귀속)**으로 남긴다.
Analytics 큐 실패 때문에 구독 자체를 실패시키지 않는다. 익명 CTA는 이 인증 기반 funnel에 포함하지 않는다.
행동 timestamp와 CTA는 클라이언트 관측이므로 마케팅 기여 추정이며 결제 사실의 증명이 아니다.
Kafka 로컬 네트워크는 신뢰된 내부 경계다. 실제 운영에서는 producer/topic ACL과 결제 provider 검증이 별도로 필요하다.

| View | 의미 |
|---|---|
| subscription_events_current | eventId 중복 제거한 서버 구독 변경 |
| subscription_ctas_current | 인증 사용자 CTA, eventId 중복 제거 |
| subscription_acquisition_attribution | 첫 활성화별 매칭 CTA/귀속 콘텐츠(미귀속 포함) |
| content_daily_acquisition | UTC 활성화일·콘텐츠·activation_source별 신규 구독 수 |
| content_subscription_funnel | UTC CTA 날짜·콘텐츠별 CTA 수, checkout 연결 CTA 수, 신규 활성화 연결 CTA 수, 전환율 |

conversion_rate는 converted_ctas / cta_clicks(0~1)다. 재활성화는 신규 획득에서 제외한다.
취소해도 과거 획득 사실은 지우지 않는다. 실제 매출이나 현재 활성 구독 수로 해석하지 않는다.
현재 모든 획득은 LOCAL_TEST이며 실제 유료 지표와 합산하면 안 된다.
여러 비율의 단순 평균 대신 분자·분모를 합쳐 계산한다. ClickHouse 물리 테이블을 직접 합산하지 않는다.

## 실행 / 업그레이드

현재 로컬 root `.env`에는 요청한 테스트 활성화를 켰다. 공유 template의 기본값은 false다.
다른 환경에서는 `LOCAL_SUBSCRIPTION_ACTIVATION_ENABLED=true`로 켜야 local activation API가 등록된다.

```powershell
# 프로젝트 루트
$env:GRADLE_USER_HOME = "$PWD\.gradle"
.\backend\gradlew.bat -p backend :user-api:bootJar :event-analytics:analyticsDistribution
docker compose build user-api analytics-jobmanager user-web
docker compose up -d clickhouse-init
docker compose up -d user-api user-web analytics-taskmanager archive-taskmanager
```

로컬 Frontend dev 실행에서는 user-api/lb 주소와 NEXT_PUBLIC_USER_API_BASE_URL을 기존 설정대로 사용한다.
Backend IDE 실행은 root `.env` 또는 해당 IDE 환경에 활성화 설정을 추가한다.
root Compose는 API/Frontend 포함, backend Compose는 기존 인프라/worker 역할을 유지한다.

5→6 최초 분석 적용 시 이전 Analytics Job을 중지하고 ANALYTICS_RESTORE_PATH를 비워 Kafka earliest로
재수집해야 이미 지난 behavior CTA도 새 분기로 처리된다. 이전 source offset 복원만으로 새 분기의 과거 데이터가
채워지지는 않는다. 기존 serving 테이블은 지우지 않고 중복 제거를 사용한다.
구독 Kafka group은 content-analytics-subscription으로 독립하며 checkpoint는 기존 Analytics 경로를 사용한다.
Kafka retention 이전 기록을 재처리하는 MinIO backfill 실행기는 이번 범위에 없다.

## 검증

```powershell
.\backend\gradlew.bat -p backend :user-api:test --tests 'com.domain.backend.subscription.*' :event-analytics:test
# 기존 서비스와 포트가 겹치지 않는 로컬에서 실행. 비어 있는 새 프로젝트만 사용한다.
.\backend\event-analytics\verify-subscription.ps1
docker compose -p ott-subscription-stage6-check down --volumes
```

MySQL 통합 테스트는 전체 V1~V10 migration, 동시 동일 요청, activation/cancel 재시도,
재활성화 신규 전환 제외, DB+Outbox rollback, 타인 checkout/만료/공개 여부,
lease 복구와 stale ack 차단, 최대 실패 및 Kafka 호출 시 DB transaction 부재를 검사한다.
실제 API smoke는 인증부터 DB/Outbox→Kafka→Flink→ClickHouse까지 연결하고,
CTA 지연 도착 전후 미귀속→귀속 변화, 중복 CTA 및 신규 전환 중복 방지를 확인한다.

2026-09-23 결과: 구독 MySQL 테스트 7개와 Analytics 테스트 16개 통과.
실제 API로 생성/활성화/취소/재활성화 6개 Outbox 이벤트가 적재됐고,
중복 CTA에도 cta_clicks=1, checkout_ctas=1, converted_ctas=1이었다.
지연 CTA 이전에는 미귀속(0), 도착 후 contentId=101로 갱신됐으며 재활성화 후에도 신규 획득은 1건이었다.
Frontend TypeScript·변경 파일 ESLint·Next production build 및 두 Compose 검증을 통과했다.
테스트용 프로젝트/볼륨은 정리했다. 브라우저 클릭 자동화·부하 테스트는 수행하지 않았다.
