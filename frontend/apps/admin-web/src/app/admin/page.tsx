"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { adminVideoApi, type AdminVideoListItem } from "@/features/video/api/adminVideoApi";
import { mediaProcessingApi, type ProcessingJobSummary } from "@/features/media-processing/api/mediaProcessingApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";

interface Summary { queued: number; processing: number; failed: number; ready: number; videos: AdminVideoListItem[]; completedJobs: ProcessingJobSummary[]; failedJobs: ProcessingJobSummary[] }
export default function Dashboard() {
  const [data, setData] = useState<Summary | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      mediaProcessingApi.listJobs({ status: "QUEUED", size: 1 }),
      mediaProcessingApi.listJobs({ status: "PROCESSING", size: 1 }),
      mediaProcessingApi.listJobs({ status: "FAILED", size: 5 }),
      adminVideoApi.listVideos({ status: "READY", size: 1 }),
      adminVideoApi.listVideos({ size: 5 }),
      mediaProcessingApi.listJobs({ status: "COMPLETED", size: 5 }),
    ]).then(([queued, processing, failed, ready, videos, completed]) => {
      if (!cancelled) { setData({ queued: queued.totalElements, processing: processing.totalElements, failed: failed.totalElements, ready: ready.totalElements, videos: videos.content, completedJobs: completed.content, failedJobs: failed.content }); setError(""); }
    }).catch(reason => { if (!cancelled) setError(userError(reason)); });
    return () => { cancelled = true; };
  }, [revision]);
  const stats = [{ label: "Uploading", value: "—", href: "/admin/videos/upload" }, { label: "Queued", value: data?.queued ?? "—", href: "/admin/processing" }, { label: "Processing", value: data?.processing ?? "—", href: "/admin/processing" }, { label: "Failed", value: data?.failed ?? "—", href: "/admin/processing" }, { label: "Ready", value: data?.ready ?? "—", href: "/admin/videos" }];
  return <><div className="page-heading"><div><span className="eyebrow">OVERVIEW</span><h1>운영 대시보드</h1><p>영상 업로드와 HLS 처리 현황</p></div><div className="actions"><button className="button" onClick={() => setRevision(value => value + 1)}>새로고침</button><Link className="button primary" href="/admin/videos/upload">+ 영상 업로드</Link></div></div>
    {error && <div className="panel error" role="alert">{error}<button className="button small" onClick={() => setRevision(value => value + 1)}>다시 시도</button></div>}
    <div className="stats">{stats.map(item => <Link className="stat panel" href={item.href} key={item.label}><span>{item.label}</span><strong>{item.value}</strong></Link>)}</div>
    <p className="muted">Uploading 전체 건수는 업로드 상태 집계 API가 없어 표시하지 않습니다.</p>
    {!data && !error && <p className="muted">운영 현황을 불러오는 중입니다…</p>}
    {data && <div className="detail-grid"><section className="panel"><div className="section-head"><h2>최근 등록 영상</h2><Link className="button small" href="/admin/videos">전체 보기</Link></div>{data.videos.length ? data.videos.map(video => <Link className="dashboard-row" href={`/admin/videos/${video.id}`} key={video.id}><div><strong>{video.title}</strong><small>VIDEO #{video.id}</small></div><StatusBadge value={video.status} /></Link>) : <p className="muted">등록된 영상이 없습니다.</p>}</section><section className="panel"><div className="section-head"><h2>최근 처리 완료</h2><Link className="button small" href="/admin/processing">전체 보기</Link></div>{data.completedJobs.length ? data.completedJobs.map(job => <Link className="dashboard-row" href={`/admin/videos/${job.videoId}`} key={job.id}><div><strong>JOB #{job.id}</strong><small>VIDEO #{job.videoId}</small></div><StatusBadge value={job.status} /></Link>) : <p className="muted">완료된 작업이 없습니다.</p>}</section><section className="panel"><div className="section-head"><h2>최근 실패</h2><Link className="button small" href="/admin/processing">전체 보기</Link></div>{data.failedJobs.length ? data.failedJobs.map(job => <Link className="dashboard-row" href={`/admin/videos/${job.videoId}`} key={job.id}><div><strong>JOB #{job.id}</strong><small>{job.errorCode ?? `VIDEO #${job.videoId}`}</small></div><StatusBadge value={job.status} /></Link>) : <p className="muted">실패한 작업이 없습니다.</p>}</section></div>}
  </>;
}
