"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { adminVideoApi, type AdminVideoListItem, type VideoStatus } from "@/features/video/api/adminVideoApi";
import type { PageResponse } from "@/shared/lib/page";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";

const statuses: { label: string; value: VideoStatus | "" }[] = [
  { label: "전체", value: "" }, { label: "등록 중", value: "DRAFT" },
  { label: "처리 중", value: "PROCESSING" }, { label: "READY", value: "READY" },
  { label: "FAILED", value: "PROCESSING_FAILED" },
];
const date = (value: string) => new Date(value).toLocaleString("ko-KR");
export default function VideosPage() {
  const [status, setStatus] = useState<VideoStatus | "">("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<AdminVideoListItem> | null>(null);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let cancelled = false;
    adminVideoApi.listVideos({ status: status || undefined, page }).then(data => {
      if (!cancelled) { setResult(data); setError(""); setLoading(false); }
    }).catch(reason => { if (!cancelled) { setError(userError(reason)); setLoading(false); } });
    return () => { cancelled = true; };
  }, [status, page, revision]);
  const visible = result?.content.filter(video => video.title.toLocaleLowerCase().includes(query.toLocaleLowerCase())) ?? [];
  return <>
    <div className="page-heading"><div><span className="eyebrow">LIBRARY</span><h1>영상 관리</h1><p>등록된 영상과 최신 HLS 처리 상태</p></div><Link className="button primary" href="/admin/videos/upload">+ 영상 업로드</Link></div>
    <section className="panel">
      <div className="section-head"><h2>영상 목록</h2><button className="button small" onClick={() => { setLoading(true); setRevision(value => value + 1); }}>새로고침</button></div>
      <div className="filters"><label>상태<select value={status} onChange={event => { setStatus(event.target.value as VideoStatus | ""); setPage(0); setLoading(true); }}>{statuses.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><label>현재 페이지 제목 검색<input value={query} onChange={event => setQuery(event.target.value)} placeholder="현재 페이지에서 검색" /></label></div>
      <p className="muted">제목 검색은 현재 페이지에만 적용됩니다. 전체 검색 API는 아직 없습니다.</p>
      {error && <div className="error" role="alert">{error}<button className="button small" onClick={() => { setLoading(true); setRevision(value => value + 1); }}>다시 시도</button></div>}
      {loading ? <p className="muted">영상 목록을 불러오는 중입니다…</p> : !error && result && <>
        {visible.length === 0 ? <p className="muted empty">{query ? "현재 페이지에 일치하는 영상이 없습니다." : "등록된 영상이 없습니다."}</p> : <div className="table-wrap"><table><thead><tr><th>영상</th><th>상태</th><th>HLS 처리</th><th>등록일</th><th>수정일</th><th>작업</th></tr></thead><tbody>{visible.map(video => <tr key={video.id}><td><div className="video-cell"><div className="thumbnail">NO IMAGE</div><div><strong>{video.title}</strong><small>VIDEO #{video.id} · SOURCE #{video.activeVideoFileId ?? "—"}</small></div></div></td><td><StatusBadge value={video.status} /></td><td>{video.processing ? <><StatusBadge value={video.processing.status} /><small>{video.processing.stage} · {video.processing.progressPercent}%</small></> : "—"}</td><td>{date(video.createdAt)}</td><td>{date(video.updatedAt)}</td><td><Link className="button small" href={`/admin/videos/${video.id}`}>상세</Link> {video.status === "READY" && <Link className="button small" href={`/admin/videos/${video.id}/watch`}>시청</Link>}</td></tr>)}</tbody></table></div>}
        <div className="pagination"><span>{result.totalElements}개 · {result.page + 1} / {Math.max(1, result.totalPages)} 페이지</span><div><button className="button small" disabled={result.first} onClick={() => { setPage(value => value - 1); setLoading(true); }}>이전</button><button className="button small" disabled={result.last} onClick={() => { setPage(value => value + 1); setLoading(true); }}>다음</button></div></div>
      </>}
    </section>
  </>;
}
