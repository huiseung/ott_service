# 8단계 — 관리자 콘텐츠 분석

관리자 메뉴 **Insights → Analytics** 또는 콘텐츠 상세의 **분석 보기**에서 접근한다.
경로는 `/admin/analytics?contentId=10`이며, 콘텐츠 ID와 UTC 시작일·종료일을 선택한다.
기본 기간은 오늘을 포함한 최근 7일, 최대 조회 기간은 양끝 날짜를 포함해 31일이다.

## 화면

- Reach: 기간 전체 순 시청자, 재생 시작, 노출·클릭, 일별 재생 시작 그래프와 데이터 표.
- Engagement: 총·평균 시청 시간, 90% 구간 관측 기준 완주율, 10% 구간별 유지율 그래프와 표, 다음 에피소드 7일 전환율.
- Acquisition: 전체 버튼 클릭, 로그인 사용자 전환 대상 클릭, 구독 절차 진입, CTA 전환율, 콘텐츠 기여 신규 구독 및 테스트 구독 건수.
- Quality: 평균·p95 시작 지연, 버퍼링 비율, 오류·치명적 오류 세션 비율, 종료 미관측 버퍼 경고.

차트 수치는 펼쳐 볼 수 있는 표로도 제공한다. 새 조회에서는 이전 결과를 숨기며,
로딩·빈 데이터·조회 오류를 구분한다. 집계가 없으면 건수는 0, 분모/측정 표본이 없으면 —다.
분석 서버 장애를 정상적인 0건으로 표시하지 않는다. 503은 안내와 재시도 버튼을 표시한다.

콘텐츠 ID는 기존 CMS ID를 사용한다. 분석 데이터가 없는 콘텐츠도 조회할 수 있으며
제목은 기존 CMS 상세 API로 가져온다. 전체 콘텐츠 순위나 새 관리자 인증 방식은 추가하지 않았다.

## API와 집계 기준

`GET /api/admin/analytics/contents/{contentId}?from=2026-09-24&to=2026-09-25`

기존 ADMIN 역할과 HTTP Basic 인증을 사용한다. 비로그인은 401, USER 역할은 403,
잘못된 ID·날짜·기간은 400, 분석 서버 조회 실패는 503이다. 응답은 `Cache-Control: no-store`다.
응답에는 contentId/from/to/queriedAt와 reach/engagement/acquisition/quality,
retention/episodes/daily가 포함된다. episode 관계는 첫 100개까지만 반환하고 잘림 여부를 표시한다.

기간별 순 시청자는 `uniqExact`로 기간 전체에서 중복 제거한다. 일별 순 시청자 합계를 쓰지 않는다.
완주율·유지율·에피소드 전환율·버퍼링·오류율은 분자·분모를 합친 후 계산한다.
평균·p95 시작 지연은 세션 측정값에서 계산하며 일별 평균·p95를 평균내지 않는다.

| 영역 | 날짜 기준 |
|---|---|
| 도달 / 일별 그래프 | 이벤트 발생일 |
| 시청 / 완주 / 유지율 | 세션 시작일. 시작 이벤트가 없는 시청 관측은 기존 정의에 따라 첫 관측일 |
| 에피소드 전환 | 사용자별 해당 회차 첫 시청일. 7일 미경과는 집계 중 표시 |
| CTA 전환 | 로그인 사용자 CTA 클릭일. 최대 7일 + checkout 30분 이후 전환 반영 가능 |
| 콘텐츠 기여 신규 구독 | 구독 활성화일. LOCAL_TEST를 별도 건수로 표시 |
| 품질 | 시작 이벤트가 있는 qoeVersion=1 세션의 시작일 |

순 시청자와 재생 시작은 실제 시청 여부가 아닌 플레이어 세션 초기화 관측 기준이다.
유지율은 해당 구간에 겹치는 시청 관측의 비율이며 단조 감소하는 생존 곡선은 아니다.
각 지표의 자세한 정의는 [Engagement](ENGAGEMENT.md), [Subscription](SUBSCRIPTIONS.md),
[QoE](QOE.md)를 따른다. 테스트 구독을 실제 유료 성과로 해석하지 않는다.

조회 시각과 최근 도달 이벤트 시각을 표시한다. 최근 도달 이벤트는 파이프라인 watermark나
전체 데이터 완전성 보장이 아니다. 별도의 7개 SELECT를 실행하므로 응답 전체가 단일
스냅샷은 아니며, 조회 도중 도착한 이벤트와 분기별 적재 차이로 숫자가 일시적으로 다를 수 있다.

## 실행과 격리

Admin API의 서버 환경 변수:

- `ANALYTICS_QUERY_URL`: IDE 실행 기본 `http://localhost:8123`, 루트 Compose는 `http://clickhouse:8123`.
- `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD`: 기존 서버 환경 변수. 브라우저로 전달하지 않는다.

루트 Compose의 admin-api에 조회 연결 설정을 추가했다. backend Compose는 인프라 전용으로 유지한다.
ClickHouse 실행/정상 상태를 admin-api의 시작 의존성으로 걸지 않았다. 분석이 중단되어도 CMS는
시작할 수 있고, 조회 요청만 503으로 실패한다. 자격 증명이 없을 때도 앱 시작은 가능하다.

조회는 고정된 SELECT와 ClickHouse 타입 매개변수를 사용하고 `readonly=1`로 실행한다.
DB 트랜잭션 안에서 HTTP를 호출하지 않는다. API 인스턴스당 동시 분석 조회 2개,
개별 HTTP 4초·서버 쿼리 2초·쿼리 메모리 256MiB·최대 스레드 2·반환 크기를 제한한다.
이는 로컬 보호 한도이며 부하 성능을 보장하는 수치가 아니다. 현재 서버 계정을 재사용하되
실제 운영에서는 분석 SELECT만 허용된 전용 계정을 사용하는 것이 적절하다.

기존 1~7단계 마이그레이션과 분석기가 실행된 환경에서:

```powershell
$env:GRADLE_USER_HOME = "$PWD/.gradle"
./backend/gradlew.bat -p backend :admin-api:bootJar
docker compose up -d --build admin-api admin-web
```

새 ClickHouse 마이그레이션은 없다. Analytics UI를 사용하려면 [분석기 실행 안내](README.md)에 따라
ClickHouse 및 기존 분석 파이프라인을 실행해야 한다. Admin UI용 localhost는 기본 3001이다.

## 검증 기록 — 2026-09-25 KST

- 관리자 분석 테스트 6개: 401/403/ADMIN 권한, 날짜 파싱, 조회 범위, HTTP 파라미터화, 장애 전파·슬롯 반환.
- 실제 ClickHouse 통합 테스트 1개: 기존 마이그레이션에 대해 7개 조회식 실행, 기간 전체 고유 사용자,
  완주율·유지율·에피소드 전환·테스트 구독·QoE·빈 데이터의 NULL 검증.
- 프론트 핵심 테스트 2개: NULL과 0 구분, 유효 날짜와 최대 31일.
- 변경 영역 타입 검사·ESLint, admin-web production build.
- 실제 브라우저 화면: 위 ClickHouse 테스트에서 저장한 합성 응답을 임시 API로 제공해 정상·빈 값·503·잘못된 기간,
  390px 모바일 폭 표시를 확인. 실제 운영자 계정/데이터는 사용하지 않았다.

관련 테스트:

```powershell
./backend/gradlew.bat -p backend :admin-api:test --tests 'com.domain.backend.analytics.*'
cd frontend/apps/admin-web
node --test src/features/analytics/*.test.mjs
node node_modules/typescript/bin/tsc --noEmit
```

`AnalyticsSqlIntegrationTest`는 `ANALYTICS_SQL_TEST_URL`을 설정했을 때만 실행한다.
기존 SQL 파일들로 초기화된 빈 일회성 ClickHouse가 필요하다. 테스트 전용 계정은
`stage8-test` / `stage8-test-only`이며 localhost에만 바인딩해서 사용한다. 실제 환경에 실행하지 않는다.
브라우저 검증용 응답은 admin-api/build 아래 생성된다. 운영 코드에는 데모 데이터가 없다.

## 단계 상태

1~7단계 데이터 수집·보존·분석에 이어 8단계 관리자 UI까지 구현했다.
전체 서비스의 종단 간 시험과 부하·성능 시험은 단계별 검증과 별도로 남아 있다.
