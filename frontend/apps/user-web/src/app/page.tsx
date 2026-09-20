"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { userApi, type Page, type VideoItem } from "@/features/api";
import { errorMessage } from "@/shared/apiClient";
import { useAuth } from "@/features/auth/AuthProvider";
export default function Home() {
  const { user } = useAuth(); const [page, setPage] = useState(0); const [data, setData] = useState<Page<VideoItem> | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [revision, setRevision] = useState(0);
  useEffect(() => { let active = true; userApi.videos(page).then(value => { if (active) { setData(value); setError(""); } }).catch(reason => { if (active) setError(errorMessage(reason)); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, [page, revision]);
  return <><div className="page-heading"><div><span className="eyebrow">OTT LIBRARY</span><h1>영상 목록</h1><p>시청할 영상을 선택하세요.</p></div></div><section className="panel"><div className="section-head"><h2>재생 가능한 영상</h2><span className="muted">{data?.totalElements ?? "—"}개</span></div>{loading && <p className="muted">영상 목록을 불러오는 중입니다…</p>}{error && <div className="error" role="alert">{error} <button className="button small" onClick={() => { setLoading(true); setRevision(value => value + 1); }}>다시 시도</button></div>}{!loading && !error && data && (data.content.length ? <><div className="video-list">{data.content.map(video => <Link className="video-item" href={user ? `/watch/${video.id}` : `/login?next=${encodeURIComponent(`/watch/${video.id}`)}`} key={video.id}><span className="video-icon">▶</span><strong>{video.title}</strong><span className="muted">시청하기 →</span></Link>)}</div><div className="pagination"><span>{data.page + 1} / {Math.max(1, data.totalPages)} 페이지</span><div><button className="button small" disabled={data.first} onClick={() => { setLoading(true); setPage(value => value - 1); }}>이전</button><button className="button small" disabled={data.last} onClick={() => { setLoading(true); setPage(value => value + 1); }}>다음</button></div></div></> : <p className="muted empty">재생 가능한 영상이 없습니다.</p>)}</section></>;
}
