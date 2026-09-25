"use client";
import { ContentCard } from "@/features/analytics/ContentCard";
import { useEffect, useState } from "react";
import { userApi, type ContentPage } from "@/features/api";
import { errorMessage } from "@/shared/apiClient";
import { useAuth } from "@/features/auth/AuthProvider";

export default function Home() {
  const { user } = useAuth();
  const [cursors, setCursors] = useState<(number | null)[]>([null]);
  const [data, setData] = useState<ContentPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const cursor = cursors[cursors.length - 1];
  useEffect(() => {
    const controller = new AbortController();
    const refresh = () => userApi.contents(cursor, controller.signal).then(value => {
      if (!controller.signal.aborted) { setData(value); setError(""); }
    }).catch(reason => {
      if (!controller.signal.aborted) { setData(null); setError(errorMessage(reason)); }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    void refresh();
    const timer = setInterval(() => void refresh(), 30000);
    const onFocus = () => void refresh();
    window.addEventListener("focus", onFocus);
    return () => { controller.abort(); clearInterval(timer); window.removeEventListener("focus", onFocus); };
  }, [cursor, revision]);
  return <>
    <div className="page-heading"><div><span className="eyebrow">OTT LIBRARY</span><h1>작품 목록</h1><p>현재 서비스 중인 작품을 만나보세요.</p></div></div>
    <section className="panel">
      <div className="section-head"><h2>시청 가능한 작품</h2><button className="button small" onClick={() => { setLoading(true); setRevision(value => value + 1); }}>새로고침</button></div>
      {loading && <p role="status">작품 목록을 불러오는 중입니다…</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {!loading && !error && data && <>
        {data.items.length ? <div className="video-list">{data.items.map(content =>
          <ContentCard content={content} loggedIn={Boolean(user)} key={content.contentId} />
        )}</div> : <p className="muted empty">현재 서비스 중인 작품이 없습니다.</p>}
        <div className="pagination"><span>{cursors.length} 페이지</span><div>
          <button className="button small" disabled={cursors.length === 1} onClick={() => { setLoading(true); setCursors(value => value.slice(0, -1)); }}>이전</button>
          <button className="button small" disabled={!data.hasNext} onClick={() => { if (data.nextCursor !== null) { setLoading(true); setCursors(value => [...value, data.nextCursor]); } }}>다음</button>
        </div></div>
      </>}
    </section>
  </>;
}
