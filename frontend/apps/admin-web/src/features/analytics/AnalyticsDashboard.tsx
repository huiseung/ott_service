"use client";
import Link from "next/link";
import { useCallback, useState, type FormEvent } from "react";
import { adminContentApi } from "@/features/content/api/adminContentApi";
import { useAdminResource } from "@/shared/components/useAdminResource";
import { ApiError } from "@/shared/lib/apiClient";
import { getAnalytics, type AnalyticsFilter } from "./api";
import { validRange } from "./presentation";
import { AnalyticsReport } from "./AnalyticsReport";

function ReportLoader({ filter }: { filter: AnalyticsFilter }) {
  const load = useCallback(async (signal: AbortSignal) => {
    try {
      const [data, content] = await Promise.all([getAnalytics(filter, signal), adminContentApi.get(filter.contentId, signal)]);
      return { data, title: (content.localizations.find(item => item.locale === "ko") ?? content.localizations[0])?.title ?? `Content #${content.id}` };
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 503) throw new Error("분석 데이터를 일시적으로 조회할 수 없습니다. 잠시 후 다시 시도하세요.");
      throw reason;
    }
  }, [filter]);
  const resource = useAdminResource(load);
  return <div aria-busy={resource.loading}>
    {resource.loading ? <section className="panel" role="status">분석 데이터를 불러오는 중입니다…</section> : resource.error ?
      <section className="panel" role="alert"><h2>조회하지 못했습니다</h2><p className="error">{resource.error}</p><button className="button" onClick={resource.reload}>다시 시도</button></section> : resource.data && <>
        <div className="section-head"><div><h2>{resource.data.title}</h2><p className="muted">{filter.from} ~ {filter.to} · UTC · Content #{filter.contentId}</p></div><button className="button" onClick={resource.reload}>새로고침</button></div>
        <AnalyticsReport data={resource.data.data} />
      </>}
  </div>;
}

export function AnalyticsDashboard({ initialContentId, initialFrom, initialTo }: { initialContentId: number | null; initialFrom: string; initialTo: string }) {
  const [filter, setFilter] = useState<AnalyticsFilter | null>(initialContentId ? { contentId: initialContentId, from: initialFrom, to: initialTo } : null);
  const [error, setError] = useState("");
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const contentId = Number(form.get("contentId")), from = String(form.get("from")), to = String(form.get("to"));
    if (!Number.isSafeInteger(contentId) || contentId <= 0 || !validRange(from, to)) {
      setError("콘텐츠 ID와 최대 31일의 올바른 날짜 범위를 입력하세요."); return;
    }
    setError(""); setFilter({ contentId, from, to });
  }
  return <>
    <div className="page-heading"><div><span className="eyebrow">CONTENT INSIGHTS</span><h1>콘텐츠 분석</h1><p>도달부터 시청, 구독 전환, 재생 품질까지 확인하세요.</p></div><Link className="button" href="/admin/contents">콘텐츠 목록</Link></div>
    <section className="panel"><form onSubmit={submit} aria-label="분석 조회 조건"><div className="filters content-filters">
      <label>콘텐츠 ID<input name="contentId" type="number" min="1" step="1" max={Number.MAX_SAFE_INTEGER} required defaultValue={initialContentId ?? ""} placeholder="예: 10" /></label>
      <label>시작일 (UTC)<input name="from" type="date" min="1970-01-01" max="2149-06-06" required defaultValue={initialFrom} /></label>
      <label>종료일 (UTC)<input name="to" type="date" min="1970-01-01" max="2149-06-06" required defaultValue={initialTo} /></label>
      <button className="button primary" type="submit">분석 조회</button>
    </div>{error && <p className="error" role="alert">{error}</p>}</form>
      <p className="muted">최대 31일을 조회할 수 있습니다. 콘텐츠 상세의 ‘분석 보기’에서도 바로 이동할 수 있습니다. 데이터는 수집 후 반영되며 과거 결과도 늦게 도착한 이벤트에 따라 달라질 수 있습니다.</p>
      <details><summary>날짜 기준과 지표 해석</summary><p>도달은 이벤트 발생일, 시청·품질은 세션 시작일, 구독 전환율은 버튼 클릭일을 기준으로 합니다. 콘텐츠 기여 구독은 활성화일 기준입니다. 서로 다른 기준의 숫자는 직접 더하거나 나누지 않습니다. —는 표본이 없거나 계산할 수 없는 값이며, 조회 장애를 0으로 표시하지 않습니다.</p></details>
    </section>
    {filter ? <ReportLoader key={`${filter.contentId}:${filter.from}:${filter.to}`} filter={filter} /> : <section className="panel empty"><h2>분석할 콘텐츠를 선택하세요</h2><p className="muted">콘텐츠 ID와 기간을 입력하면 지표가 표시됩니다.</p></section>}
  </>;
}
