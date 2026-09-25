import { AnalyticsChart } from "./AnalyticsChart";
import type { AnalyticsDashboardData } from "./api";
import { daysBetween, metric, percent } from "./presentation";

function Metrics({ items }: { items: { label: string; value: string; note?: string }[] }) {
  return <dl className="analytics-metrics">{items.map(item => <div key={item.label}><dt>{item.label}</dt><dd>{item.value}</dd>{item.note && <small>{item.note}</small>}</div>)}</dl>;
}

export function AnalyticsReport({ data }: { data: AnalyticsDashboardData }) {
  const { reach, engagement, acquisition, quality } = data;
  const byDate = new Map(data.daily.map(row => [row.date, row]));
  const daily = daysBetween(data.from, data.to).map(date => byDate.get(date) ?? { date, playStarts: 0, uniqueViewers: 0, impressions: 0, clicks: 0 });
  const empty = !reach.playStarts && !reach.impressions && !reach.clicks && !reach.detailViews && !reach.ctaClicks
    && !engagement.sessions && !acquisition.ctaClicks && !acquisition.attributedSubscriptions && !quality.sessions;
  return <>
    <p className="muted analytics-meta">조회 시각: {new Date(data.queriedAt).toLocaleString("ko-KR")} · 최근 도달 이벤트: {reach.latestEventAt ? `${reach.latestEventAt} UTC` : "없음"}</p>
    {empty && <div className="panel notice" role="status"><h2>선택한 기간에 수집된 데이터가 없습니다</h2><p>기간을 바꾸거나 콘텐츠 재생·노출 이후 다시 조회하세요. 아래 0은 관측 건수가 없음을, —는 계산할 표본이 없음을 뜻합니다.</p></div>}
    <section className="panel" aria-labelledby="reach-title"><span className="eyebrow">REACH</span><h2 id="reach-title">얼마나 많은 시청자에게 도달했나요?</h2>
      <Metrics items={[
        { label: "순 시청자", value: metric(reach.uniqueViewers), note: "선택 기간 전체에서 중복 제거" },
        { label: "재생 시작", value: metric(reach.playStarts), note: "플레이어 세션 초기화 기준" },
        { label: "콘텐츠 노출", value: metric(reach.impressions) }, { label: "콘텐츠 클릭", value: metric(reach.clicks) },
      ]} />
      <h3>일별 재생 시작</h3><AnalyticsChart title="일별 재생 시작" points={daily.map(row => ({ label: row.date.slice(5), value: row.playStarts }))} />
      <details><summary>일별 도달 데이터</summary><div className="table-wrap"><table><caption className="sr-only">UTC 날짜별 재생 시작, 순 시청자, 노출과 클릭</caption><thead><tr><th scope="col">날짜 (UTC)</th><th scope="col">재생 시작</th><th scope="col">순 시청자</th><th scope="col">노출</th><th scope="col">클릭</th></tr></thead><tbody>{daily.map(row => <tr key={row.date}><th scope="row">{row.date}</th><td>{metric(row.playStarts)}</td><td>{metric(row.uniqueViewers)}</td><td>{metric(row.impressions)}</td><td>{metric(row.clicks)}</td></tr>)}</tbody></table></div></details>
    </section>
    <section className="panel" aria-labelledby="engagement-title"><span className="eyebrow">ENGAGEMENT</span><h2 id="engagement-title">얼마나 오래, 끝까지 시청했나요?</h2>
      <Metrics items={[
        { label: "총 시청 시간", value: `${metric(engagement.watchHours, 2)}시간` },
        { label: "평균 시청 시간", value: engagement.averageWatchSeconds === null ? "—" : `${metric(engagement.averageWatchSeconds / 60, 1)}분`, note: `${metric(engagement.sessions)}개 관측 세션 기준` },
        { label: "완주율", value: percent(engagement.completionRate), note: `유효 ${metric(engagement.eligibleSessions)}개 중 ${metric(engagement.completedSessions)}개 완주` },
      ]} />
      <p className="muted">완주는 전체 영상 구간의 90% 이상을 관측한 경우입니다. 탐색으로 건너뛴 구간은 제외하며, 반복 시청 시간은 포함합니다.</p>
      <h3>시청 구간별 유지율</h3>
      <p className="muted">각 구간의 일부를 시청한 세션 비율입니다. 중간 구간을 건너뛰면 뒤쪽 구간의 비율이 더 높을 수 있습니다.</p>
      <AnalyticsChart title="시청 구간별 유지율" percent points={data.retention.filter(row => row.rate !== null).map(row => ({ label: `${row.bucket}–${row.bucket + 10}%`, value: (row.rate ?? 0) * 100 }))} />
      <details><summary>유지율 표본 보기</summary><div className="table-wrap"><table><caption className="sr-only">영상 10% 구간별 관측 세션</caption><thead><tr><th scope="col">영상 구간</th><th scope="col">관측 세션</th><th scope="col">대상 세션</th><th scope="col">유지율</th></tr></thead><tbody>{data.retention.map(row => <tr key={row.bucket}><th scope="row">{row.bucket}–{row.bucket + 10}%</th><td>{metric(row.watchedSessions)}</td><td>{metric(row.eligibleSessions)}</td><td>{percent(row.rate)}</td></tr>)}</tbody></table></div></details>
      <h3 className="analytics-subheading">다음 에피소드 전환</h3><p className="muted">첫 시청 이후 7일 이내 다음 회차를 시청한 사용자 비율입니다. ‘집계 중’인 집단은 결과가 더 늘어날 수 있습니다.</p>
      {!data.episodes.length ? <p className="muted">다음 회차가 있는 시리즈의 시청 데이터가 없습니다.</p> : <div className="table-wrap"><table><caption className="sr-only">에피소드별 7일 전환율</caption><thead><tr><th scope="col">에피소드 ID</th><th scope="col">대상 시청자</th><th scope="col">전환 시청자</th><th scope="col">전환율</th><th scope="col">관측 기간</th></tr></thead><tbody>{data.episodes.map(row => <tr key={`${row.episodeId}-${row.nextEpisodeId}`}><th scope="row">#{row.episodeId} → #{row.nextEpisodeId}</th><td>{metric(row.eligibleViewers)}</td><td>{metric(row.convertedViewers)}</td><td>{percent(row.rate)}</td><td><span className="badge">{row.mature ? "7일 경과" : "집계 중"}</span></td></tr>)}</tbody></table></div>}
      {data.episodesTruncated && <p className="muted">첫 100개 에피소드 관계를 표시합니다.</p>}
    </section>
    <section className="panel" aria-labelledby="acquisition-title"><span className="eyebrow">ACQUISITION</span><h2 id="acquisition-title">구독으로 이어졌나요?</h2>
      <Metrics items={[
        { label: "전체 구독 버튼 클릭", value: metric(reach.ctaClicks), note: "익명 클릭 포함" },
        { label: "전환 분석 대상 클릭", value: metric(acquisition.ctaClicks), note: "로그인 사용자 클릭" },
        { label: "구독 절차 진입", value: metric(acquisition.checkoutCtas), note: "클릭 기준 중복 제거" },
        { label: "구독 전환율", value: percent(acquisition.conversionRate), note: `${metric(acquisition.convertedCtas)}개 클릭에서 신규 구독 발생` },
        { label: "콘텐츠 기여 신규 구독", value: metric(acquisition.attributedSubscriptions), note: `이 중 테스트 구독 ${metric(acquisition.testSubscriptions)}건` },
      ]} />
      <p className="muted">전환율은 선택 기간의 버튼 클릭을 기준으로, 콘텐츠 기여 구독은 구독 활성화일을 기준으로 집계합니다. 클릭 후 최대 7일과 구독 절차 30분까지 결과가 추가될 수 있습니다. 테스트 구독은 실제 결제 성과가 아닙니다.</p>
    </section>
    <section className="panel" aria-labelledby="quality-title"><span className="eyebrow">QUALITY</span><h2 id="quality-title">재생 경험은 원활했나요?</h2>
      <Metrics items={[
        { label: "평균 시작 지연", value: quality.averageStartupMs === null ? "—" : `${metric(quality.averageStartupMs / 1000, 2)}초`, note: `측정 세션 ${metric(quality.startupSamples)}개` },
        { label: "시작 지연 p95", value: quality.p95StartupMs === null ? "—" : `${metric(quality.p95StartupMs / 1000, 2)}초`, note: "시작 지연의 근사 95백분위" },
        { label: "버퍼링 비율", value: percent(quality.rebufferRatio), note: `버퍼링 ${metric(quality.bufferCount)}회` },
        { label: "오류 세션 비율", value: percent(quality.errorRate), note: `${metric(quality.sessions)}개 중 ${metric(quality.errorSessions)}개` },
        { label: "치명적 오류 비율", value: percent(quality.fatalErrorRate), note: `${metric(quality.fatalErrorSessions)}개 세션` },
      ]} />
      <p className="muted">시작 지연은 첫 재생 요청부터 재생 진행까지, 버퍼링 비율은 시청·버퍼링 시간 합계 대비 대기 시간입니다. 초기 로딩·일시정지·탐색 대기는 제외합니다. 품질 계측을 적용한 이후의 세션만 포함됩니다.</p>
      {quality.openBufferSessions > 0 && <p className="analytics-warning" role="status">{metric(quality.openBufferSessions)}개 세션의 버퍼 종료가 아직 관측되지 않아 대기 시간이 과소 집계될 수 있습니다.</p>}
    </section>
  </>;
}
