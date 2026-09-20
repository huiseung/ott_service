"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { mediaProcessingApi, type ProcessingJobSummary } from "@/features/media-processing/api/mediaProcessingApi";
import type { JobStatus } from "@/features/video/api/adminVideoApi";
import type { PageResponse } from "@/shared/lib/page";
import { ProgressBar, StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";

const statuses: (JobStatus | "")[] = ["", "QUEUED", "PROCESSING", "RETRY_WAIT", "COMPLETED", "FAILED", "SUPERSEDED"];
export default function ProcessingPage() {
  const [status, setStatus] = useState<JobStatus | "">("");
  const [videoId, setVideoId] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ProcessingJobSummary> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);
  const refresh = () => setRevision(value => value + 1);
  useEffect(() => {
    let cancelled = false;
    mediaProcessingApi.listJobs({ status: status || undefined, videoId: videoId ? Number(videoId) : undefined, page }).then(data => {
      if (!cancelled) { setResult(data); setError(""); setLoading(false); }
    }).catch(reason => { if (!cancelled) { setError(userError(reason)); setLoading(false); } });
    return () => { cancelled = true; };
  }, [status, videoId, page, revision]);
  const active = result?.content.some(job => job.status === "PROCESSING" || job.status === "QUEUED" || job.status === "RETRY_WAIT");
  const processing = result?.content.some(job => job.status === "PROCESSING");
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => { if (!document.hidden) refresh(); }, processing ? 3000 : 5000);
    const visible = () => { if (!document.hidden) refresh(); };
    document.addEventListener("visibilitychange", visible);
    return () => { clearInterval(timer); document.removeEventListener("visibilitychange", visible); };
  }, [active, processing]);
  async function retry(jobId: number) {
    setBusy(jobId);
    try { await mediaProcessingApi.retry(jobId); refresh(); }
    catch (reason) { setError(userError(reason)); }
    finally { setBusy(null); }
  }
  return <>
    <div className="page-heading"><div><span className="eyebrow">MEDIA PIPELINE</span><h1>HLS Processing</h1><p>작업 상태와 예상 인코딩 진행률을 확인합니다.</p></div><button className="button" onClick={refresh}>새로고침</button></div>
    <section className="panel"><div className="section-head"><h2>처리 작업</h2><span className="muted">{result?.totalElements ?? "—"}개 작업</span></div>
      <div className="filters"><label>상태<select value={status} onChange={event => { setStatus(event.target.value as JobStatus | ""); setPage(0); setLoading(true); }}>{statuses.map(item => <option key={item} value={item}>{item || "전체"}</option>)}</select></label><label>Video ID<input type="number" min="1" value={videoId} placeholder="전체 영상" onChange={event => { setVideoId(event.target.value); setPage(0); setLoading(true); }} /></label></div>
      {error && <div className="error" role="alert">{error}<button className="button small" onClick={refresh}>다시 시도</button></div>}
      {loading && !result ? <p className="muted">작업 목록을 불러오는 중입니다…</p> : !error && result && <>
        {result.content.length === 0 ? <p className="muted empty">조건에 맞는 처리 작업이 없습니다.</p> : <div className="table-wrap"><table><thead><tr><th>Job / Video</th><th>상태</th><th>단계 / 진행률</th><th>시도 / Worker</th><th>수정일</th><th>작업</th></tr></thead><tbody>{result.content.map(job => <tr key={job.id}><td><strong>JOB #{job.id}</strong><small>VIDEO #{job.videoId} · FILE #{job.videoFileId}</small></td><td><StatusBadge value={job.status} /></td><td className="progress-cell"><strong>{job.stage}</strong><ProgressBar value={job.progressPercent} label={`Job ${job.id} 예상 진행률`} /><small>예상 진행률 {job.progressPercent}%</small>{job.errorCode && <small className="error">{job.errorCode}</small>}{job.errorMessage && <details><summary>오류 상세</summary><p>{job.errorMessage}</p></details>}</td><td>{job.attempt}<small>{job.workerId ?? "—"}</small></td><td>{new Date(job.updatedAt).toLocaleString("ko-KR")}</td><td><div className="actions"><Link className="button small" href={`/admin/videos/${job.videoId}`}>영상</Link>{job.status === "FAILED" && <button className="button small primary" disabled={busy === job.id} onClick={() => void retry(job.id)}>다시 처리</button>}</div></td></tr>)}</tbody></table></div>}
        <div className="pagination"><span>{result.totalElements}개 · {result.page + 1} / {Math.max(1, result.totalPages)} 페이지</span><div><button className="button small" disabled={result.first} onClick={() => { setPage(value => value - 1); setLoading(true); }}>이전</button><button className="button small" disabled={result.last} onClick={() => { setPage(value => value + 1); setLoading(true); }}>다음</button></div></div>
      </>}
    </section>
  </>;
}
